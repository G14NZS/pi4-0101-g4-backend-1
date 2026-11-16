package br.com.reservice.agendamento;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT9 cancelarAgendamento, alterando a tabela CT18 (agendamento). */
class CancelarAgendamentoTest extends TesteIntegracao {

    private static final String MARIA = "+5519988887777";
    private static final String JOANA = "+5519911112222";

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();

    private int anaId;
    private int manicureId;
    private int mariaId;
    /** Uma semana à frente, às 14:00, dentro da grade da Ana. */
    private LocalDateTime semanaQueVem;
    private int agendamentoDaMaria;

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        manicureId = new ServicoRepositorio(banco).inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        try (Connection c = banco.conectar()) {
            ClienteRepositorio clientes = new ClienteRepositorio();
            mariaId = clientes.inserir(c, "Maria Souza", MARIA).id();
            clientes.inserir(c, "Joana Lima", JOANA);
        }
        semanaQueVem = Banco.agora().plusDays(7).withHour(14).withMinute(0).withSecond(0);
        agendamentoDaMaria = agendar(semanaQueVem);
    }

    /** Grava direto no banco: assim dá para usar datas passadas. */
    private int agendar(LocalDateTime inicio) throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, anaId, manicureId, mariaId, inicio, inicio.plusMinutes(45)).id();
        }
    }

    private HttpResponse<String> cancelar(Object agendamentoId, String telefone) throws Exception {
        return patch("/agendamentos/" + agendamentoId + "/cancelamento",
                "{\"clienteTelefone\":\"" + telefone + "\"}");
    }

    private String status(int agendamentoId) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT status FROM agendamento WHERE id = ?")) {
            ps.setInt(1, agendamentoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    void cancelaOAgendamentoFuturoDoProprioTelefone() throws Exception {
        LocalDateTime antes = Banco.agora();

        HttpResponse<String> r = cancelar(agendamentoDaMaria, MARIA);

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(3, corpo.size(), "só os campos da ficha: " + corpo);
        assertEquals(agendamentoDaMaria, corpo.get("agendamentoId").asInt());
        assertEquals("cancelado", corpo.get("status").asText());
        LocalDateTime atualizadoEm = LocalDateTime.parse(corpo.get("atualizadoEm").asText());
        assertTrue(!atualizadoEm.isBefore(antes) && !atualizadoEm.isAfter(Banco.agora()), atualizadoEm.toString());

        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM agendamento WHERE id = ?")) {
            ps.setInt(1, agendamentoDaMaria);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("cancelado", rs.getString("status"));
                assertEquals(atualizadoEm, rs.getObject("atualizado_em", LocalDateTime.class));
                assertEquals(semanaQueVem, rs.getObject("inicio", LocalDateTime.class), "o horário não muda");
            }
        }
    }

    @Test
    void agendamentoCanceladoSaiDaListaDoCliente() throws Exception {
        cancelar(agendamentoDaMaria, MARIA);

        JsonNode lista = json(get("/agendamentos/do-cliente?clienteTelefone=%2B5519988887777")).get("agendamentos");

        assertEquals(0, lista.size(), lista.toString());
    }

    @Test
    void horarioCanceladoFicaLivreParaOutroAgendamento() throws Exception {
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO horario_atendimento (profissional_id, dia_semana, hora_inicio, hora_fim)"
                        + " VALUES (?, ?, '09:00', '18:00')")) {
            ps.setInt(1, anaId);
            ps.setString(2, HorarioAtendimento.diaSemana(semanaQueVem.getDayOfWeek()));
            ps.executeUpdate();
        }
        String pedidoDaJoana = "{\"servicoId\":" + manicureId + ",\"inicio\":\"" + semanaQueVem
                + ":00\",\"clienteTelefone\":\"" + JOANA + "\"}";
        assertErro(409, "HORARIO_INDISPONIVEL", post("/agendamentos", pedidoDaJoana, null));

        assertEquals(200, cancelar(agendamentoDaMaria, MARIA).statusCode());

        HttpResponse<String> r = post("/agendamentos", pedidoDaJoana, null);
        assertEquals(201, r.statusCode(), r.body());
    }

    @Test
    void telefoneComEspacosNasPontasEhAceito() throws Exception {
        assertEquals(200, cancelar(agendamentoDaMaria, "  " + MARIA + " ").statusCode());
    }

    // ---- AGENDAMENTO_NAO_ENCONTRADO ----

    @Test
    void agendamentoInexistenteDaAgendamentoNaoEncontrado() throws Exception {
        assertErro(404, "AGENDAMENTO_NAO_ENCONTRADO", cancelar(agendamentoDaMaria + 1000, MARIA));
    }

    @Test
    void telefoneDeOutroClienteDaAgendamentoNaoEncontradoENaoCancela() throws Exception {
        assertErro(404, "AGENDAMENTO_NAO_ENCONTRADO", cancelar(agendamentoDaMaria, JOANA));
        assertEquals("confirmado", status(agendamentoDaMaria));
    }

    @Test
    void telefoneNaoCadastradoDaAgendamentoNaoEncontrado() throws Exception {
        assertErro(404, "AGENDAMENTO_NAO_ENCONTRADO", cancelar(agendamentoDaMaria, "+5511900001111"));
        assertEquals("confirmado", status(agendamentoDaMaria));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"clienteTelefone\":null}", "{\"clienteTelefone\":5519988887777}",
            "{\"clienteTelefone\":\"\"}", "{\"clienteTelefone\":\"5519988887777\"}"})
    void semOTelefoneDoAgendamentoDaAgendamentoNaoEncontrado(String corpo) throws Exception {
        assertErro(404, "AGENDAMENTO_NAO_ENCONTRADO",
                patch("/agendamentos/" + agendamentoDaMaria + "/cancelamento", corpo));
        assertEquals("confirmado", status(agendamentoDaMaria));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999"})
    void idQueNaoEhDeAgendamentoDaAgendamentoNaoEncontrado(String id) throws Exception {
        assertErro(404, "AGENDAMENTO_NAO_ENCONTRADO", cancelar(id, MARIA));
    }

    @Test
    void naoContaSeOAgendamentoExisteParaQuemNaoEhODono() throws Exception {
        HttpResponse<String> deOutro = cancelar(agendamentoDaMaria, JOANA);
        HttpResponse<String> inexistente = cancelar(agendamentoDaMaria + 1000, JOANA);

        assertEquals(inexistente.statusCode(), deOutro.statusCode());
        assertEquals(inexistente.body(), deOutro.body());
    }

    // ---- AGENDAMENTO_JA_CANCELADO ----

    @Test
    void cancelarDuasVezesDaAgendamentoJaCancelado() throws Exception {
        assertEquals(200, cancelar(agendamentoDaMaria, MARIA).statusCode());

        assertErro(409, "AGENDAMENTO_JA_CANCELADO", cancelar(agendamentoDaMaria, MARIA));
    }

    @Test
    void canceladoQueJaPassouDaAgendamentoJaCancelado() throws Exception {
        int passado = agendar(Banco.agora().minusDays(1));
        try (Connection c = banco.conectar()) {
            agendamentos.cancelar(c, passado, Banco.agora().minusDays(2));
        }

        assertErro(409, "AGENDAMENTO_JA_CANCELADO", cancelar(passado, MARIA));
    }

    @Test
    void cancelamentosSimultaneosCancelamSoUmaVez() throws Exception {
        int pedidos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                tarefas.add(() -> cancelar(agendamentoDaMaria, MARIA).statusCode());
            }
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tarefas)) {
                status.add(f.get());
            }

            assertEquals(1, status.stream().filter(s -> s == 200).count(), status.toString());
            assertEquals(pedidos - 1, status.stream().filter(s -> s == 409).count(), status.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- AGENDAMENTO_JA_PASSOU ----

    @Test
    void agendamentoDeOntemDaAgendamentoJaPassou() throws Exception {
        int ontem = agendar(Banco.agora().minusDays(1));

        assertErro(409, "AGENDAMENTO_JA_PASSOU", cancelar(ontem, MARIA));
        assertEquals("confirmado", status(ontem));
    }

    @Test
    void agendamentoQueJaComecouDaAgendamentoJaPassou() throws Exception {
        int emAndamento = agendar(Banco.agora().minus(Duration.ofMinutes(10)));

        assertErro(409, "AGENDAMENTO_JA_PASSOU", cancelar(emAndamento, MARIA));
        assertEquals("confirmado", status(emAndamento));
    }

    @Test
    void agendamentoQueComecaDaquiAUmMinutoAindaPodeSerCancelado() throws Exception {
        int quaseNaHora = agendar(Banco.agora().plusMinutes(1));

        assertEquals(200, cancelar(quaseNaHora, MARIA).statusCode());
    }

    // ---- DADOS_INVALIDOS ----

    @Test
    void corpoQueNaoEhJsonDaDadosInvalidos() throws Exception {
        assertErro(400, "DADOS_INVALIDOS", patch("/agendamentos/" + agendamentoDaMaria + "/cancelamento", "x"));
    }
}
