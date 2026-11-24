package br.com.reservice.agendamento;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.horario.HorarioAtendimento;
import br.com.reservice.servico.ServicoRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT7 criarAgendamento, gravando nas tabelas CT17 (cliente) e CT18 (agendamento). */
class CriarAgendamentoTest extends TesteIntegracao {

    private static final String MARIA = "+5519988887777";

    /** Daqui a uma semana, para nunca cair no passado; a grade da Ana é montada para esse dia da semana. */
    private LocalDate dia;
    private int anaId;
    private int manicureId;

    @BeforeEach
    void prepararAgendaDaAna() throws Exception {
        dia = LocalDate.now(Banco.FUSO).plusDays(7);
        anaId = criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        manicureId = new ServicoRepositorio(banco).inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        inserirFaixa(anaId, dia, "09:00", "12:00");
        inserirFaixa(anaId, dia, "14:00", "18:00");
    }

    private String pedido(int servicoId, String inicio, String telefone, String nome) {
        return "{\"servicoId\":" + servicoId + ",\"inicio\":\"" + inicio + "\",\"clienteTelefone\":\"" + telefone + "\""
                + (nome == null ? "" : ",\"clienteNome\":\"" + nome + "\"") + "}";
    }

    private String as(String hora) {
        return dia + "T" + hora;
    }

    @Test
    void clienteNovoCriaCadastroEAgendamentoConfirmado() throws Exception {
        HttpResponse<String> r = post("/agendamentos", pedido(manicureId, as("14:00:00"), MARIA, " Maria Souza "), null);

        assertEquals(201, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(8, corpo.size(), "só os campos da ficha: " + corpo);
        assertTrue(corpo.get("agendamentoId").asInt() > 0);
        assertEquals(manicureId, corpo.get("servicoId").asInt());
        assertEquals("Manicure simples", corpo.get("servicoNome").asText());
        assertEquals(as("14:00:00"), corpo.get("inicio").asText());
        assertEquals(as("14:45:00"), corpo.get("fim").asText());
        assertTrue(corpo.get("clienteId").asInt() > 0);
        assertEquals("Maria Souza", corpo.get("clienteNome").asText());
        assertEquals("confirmado", corpo.get("status").asText());

        try (Connection c = banco.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM agendamento")) {
            assertTrue(rs.next());
            assertEquals(anaId, rs.getInt("profissional_id"));
            assertEquals(manicureId, rs.getInt("servico_id"));
            assertEquals(corpo.get("clienteId").asInt(), rs.getInt("cliente_id"));
            assertEquals(LocalDateTime.parse(as("14:00:00")), rs.getObject("inicio", LocalDateTime.class));
            assertEquals(LocalDateTime.parse(as("14:45:00")), rs.getObject("fim", LocalDateTime.class));
            assertEquals("confirmado", rs.getString("status"));
            assertEquals("pendente", rs.getString("lembrete_status"));
            assertNull(rs.getObject("lembrete_registrado_em"));
        }
        assertEquals(1, contar("cliente"));
    }

    @Test
    void clienteJaCadastradoNaoPrecisaDeNomeENaoEhDuplicado() throws Exception {
        int clienteId = json(post("/agendamentos", pedido(manicureId, as("09:00:00"), MARIA, "Maria Souza"), null))
                .get("clienteId").asInt();

        HttpResponse<String> r = post("/agendamentos", pedido(manicureId, as("10:00:00"), MARIA, null), null);

        assertEquals(201, r.statusCode(), r.body());
        assertEquals(clienteId, json(r).get("clienteId").asInt());
        assertEquals("Maria Souza", json(r).get("clienteNome").asText(), "usa o nome já cadastrado");
        assertEquals(1, contar("cliente"));
        assertEquals(2, contar("agendamento"));
    }

    @Test
    void aceitaAtendimentoQueTerminaJustoNoFimDaFaixaEColadoEmOutro() throws Exception {
        assertEquals(201, post("/agendamentos", pedido(manicureId, as("11:15:00"), MARIA, "Maria"), null).statusCode());
        assertEquals(201, post("/agendamentos", pedido(manicureId, as("10:30:00"), MARIA, null), null).statusCode());
    }

    @Test
    void horarioDeAgendamentoCanceladoPodeSerReusado() throws Exception {
        post("/agendamentos", pedido(manicureId, as("14:00:00"), MARIA, "Maria"), null);
        try (Connection c = banco.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE agendamento SET status = 'cancelado'");
        }

        assertEquals(201, post("/agendamentos", pedido(manicureId, as("14:00:00"), "+5519911112222", "Joana"), null)
                .statusCode());
    }

    // ---- SERVICO_NAO_ENCONTRADO ----

    @Test
    void servicoInexistenteDaServicoNaoEncontrado() throws Exception {
        assertErro(404, "SERVICO_NAO_ENCONTRADO",
                post("/agendamentos", pedido(manicureId + 1000, as("14:00:00"), MARIA, "Maria"), null));
    }

    // ---- DATA_INVALIDA ----

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01T10:00:00", "20/10/2026 14:00", "2026-10-20", "amanhã", "",
            "2020-01-06T10:00:00"})
    void inicioForaDoFormatoOuNoPassadoDaDataInvalida(String inicio) throws Exception {
        assertErro(400, "DATA_INVALIDA", post("/agendamentos", pedido(manicureId, inicio, MARIA, "Maria"), null));
    }

    @Test
    void inicioAusenteOuComSegundosDaDataInvalida() throws Exception {
        assertErro(400, "DATA_INVALIDA", post("/agendamentos",
                "{\"servicoId\":" + manicureId + ",\"clienteTelefone\":\"" + MARIA + "\",\"clienteNome\":\"M\"}", null));
        assertErro(400, "DATA_INVALIDA",
                post("/agendamentos", pedido(manicureId, as("14:00:30"), MARIA, "Maria"), null));
    }

    @Test
    void inicioUmMinutoAtrasDaDataInvalida() throws Exception {
        String umMinutoAtras = Banco.agora().minusMinutes(1).withSecond(0).toString();
        assertErro(400, "DATA_INVALIDA", post("/agendamentos", pedido(manicureId, umMinutoAtras, MARIA, "M"), null));
    }

    // ---- TELEFONE_INVALIDO ----

    @ParameterizedTest
    @ValueSource(strings = {"19988887777", "5519988887777", "+55199888877", "+551998888777766", "+5501988887777",
            "+1 415 555 0100", "+55 19 98888-7777", ""})
    void telefoneForaDoFormatoDaTelefoneInvalido(String telefone) throws Exception {
        assertErro(400, "TELEFONE_INVALIDO",
                post("/agendamentos", pedido(manicureId, as("14:00:00"), telefone, "Maria"), null));
    }

    @Test
    void telefoneFixoComOitoDigitosEhAceito() throws Exception {
        assertEquals(201, post("/agendamentos", pedido(manicureId, as("14:00:00"), "+551932221111", "Maria"), null)
                .statusCode());
    }

    // ---- NOME_OBRIGATORIO ----

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"clienteNome\":\"\"", ",\"clienteNome\":\"   \"", ",\"clienteNome\":null"})
    void telefoneNovoSemNomeDaNomeObrigatorio(String campoNome) throws Exception {
        String corpo = "{\"servicoId\":" + manicureId + ",\"inicio\":\"" + as("14:00:00")
                + "\",\"clienteTelefone\":\"" + MARIA + "\"" + campoNome + "}";

        assertErro(400, "NOME_OBRIGATORIO", post("/agendamentos", corpo, null));
        assertEquals(0, contar("cliente"));
        assertEquals(0, contar("agendamento"));
    }

    // ---- HORARIO_INDISPONIVEL ----

    @ParameterizedTest
    @ValueSource(strings = {
            "08:30:00", // começa antes da grade
            "11:30:00", // termina depois do fim da faixa da manhã
            "12:30:00", // no intervalo entre as faixas
            "17:30:00", // passa do fim do dia
            "23:30:00"  // atravessaria a meia-noite
    })
    void foraDaGradeDaHorarioIndisponivel(String hora) throws Exception {
        assertErro(409, "HORARIO_INDISPONIVEL",
                post("/agendamentos", pedido(manicureId, as(hora), MARIA, "Maria"), null));
        assertEquals(0, contar("cliente"), "cliente novo não fica gravado quando o agendamento falha");
    }

    @Test
    void diaSemGradeDaHorarioIndisponivel() throws Exception {
        assertErro(409, "HORARIO_INDISPONIVEL",
                post("/agendamentos", pedido(manicureId, dia.plusDays(1) + "T14:00:00", MARIA, "Maria"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"14:30:00", "14:00:00", "14:45:00", "15:00:00"})
    void sobrepostoAOutroAgendamentoDaHorarioIndisponivel(String hora) throws Exception {
        assertEquals(201, post("/agendamentos", pedido(manicureId, as("14:30:00"), MARIA, "Maria"), null).statusCode());

        assertErro(409, "HORARIO_INDISPONIVEL",
                post("/agendamentos", pedido(manicureId, as(hora), "+5519911112222", "Joana"), null));
    }

    @Test
    void agendamentoDeOutroProfissionalNaoConflita() throws Exception {
        int bia = criarProfissional("Bia", "bia@exemplo.com", "x", "+5519977776666", "bia-unhas");
        int escova = new ServicoRepositorio(banco).inserir(bia, "Escova", 60, new BigDecimal("50.00")).id();
        inserirFaixa(bia, dia, "09:00", "18:00");
        assertEquals(201, post("/agendamentos", pedido(manicureId, as("14:00:00"), MARIA, "Maria"), null).statusCode());

        assertEquals(201, post("/agendamentos", pedido(escova, as("14:00:00"), "+5519911112222", "Joana"), null)
                .statusCode());
    }

    @Test
    void pedidosSimultaneosNoMesmoHorarioGravamSoUm() throws Exception {
        int pedidos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                String telefone = "+55199000000" + (10 + i);
                tarefas.add(() -> post("/agendamentos",
                        pedido(manicureId, as("15:00:00"), telefone, "Cliente " + telefone), null).statusCode());
            }
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tarefas)) {
                status.add(f.get());
            }

            assertEquals(1, status.stream().filter(s -> s == 201).count(), status.toString());
            assertEquals(pedidos - 1, status.stream().filter(s -> s == 409).count(), status.toString());
            assertEquals(1, contar("agendamento"));
            assertEquals(1, contar("cliente"), "clientes dos pedidos recusados não ficam gravados");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void mesmoClienteNovoEmPedidosSimultaneosGeraUmSoCadastro() throws Exception {
        int bia = criarProfissional("Bia", "bia@exemplo.com", "x", "+5519977776666", "bia-unhas");
        int escova = new ServicoRepositorio(banco).inserir(bia, "Escova", 60, new BigDecimal("50.00")).id();
        inserirFaixa(bia, dia, "09:00", "18:00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<HttpResponse<String>>> tarefas = List.of(
                    () -> post("/agendamentos", pedido(manicureId, as("15:00:00"), MARIA, "Maria"), null),
                    () -> post("/agendamentos", pedido(escova, as("15:00:00"), MARIA, "Maria"), null));
            List<Integer> clienteIds = new ArrayList<>();
            for (Future<HttpResponse<String>> f : pool.invokeAll(tarefas)) {
                assertEquals(201, f.get().statusCode(), f.get().body());
                clienteIds.add(json(f.get()).get("clienteId").asInt());
            }

            assertEquals(clienteIds.get(0), clienteIds.get(1));
            assertEquals(1, contar("cliente"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- DADOS_INVALIDOS ----

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"inicio\":\"2030-01-07T10:00:00\",\"clienteTelefone\":\"+5519988887777\",\"clienteNome\":\"M\"}",
            "{\"servicoId\":\"12\",\"inicio\":\"2030-01-07T10:00:00\",\"clienteTelefone\":\"+5519988887777\"}",
            "{\"servicoId\":1.5,\"inicio\":\"2030-01-07T10:00:00\",\"clienteTelefone\":\"+5519988887777\"}",
            "{\"servicoId\":1,\"inicio\":\"2030-01-07T10:00:00\",\"clienteTelefone\":\"+5519988887777\",\"clienteNome\":7}",
            "não é json",
            "[]"
    })
    void corpoMalformadoDaDadosInvalidos(String corpo) throws Exception {
        assertErro(400, "DADOS_INVALIDOS", post("/agendamentos", corpo, null));
    }

    @Test
    void nomeMaiorQueAColunaDaDadosInvalidos() throws Exception {
        String nome = "a".repeat(AgendamentoRecurso.NOME_TAMANHO_MAXIMO + 1);
        assertErro(400, "DADOS_INVALIDOS", post("/agendamentos", pedido(manicureId, as("14:00:00"), MARIA, nome), null));
    }

    private void inserirFaixa(int profissionalId, LocalDate dia, String inicio, String fim) throws Exception {
        String sql = "INSERT INTO horario_atendimento (profissional_id, dia_semana, hora_inicio, hora_fim)"
                + " VALUES (?, ?, ?, ?)";
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, HorarioAtendimento.diaSemana(dia.getDayOfWeek()));
            ps.setString(3, inicio);
            ps.setString(4, fim);
            ps.executeUpdate();
        }
    }

    private int contar(String tabela) throws Exception {
        try (Connection c = banco.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
