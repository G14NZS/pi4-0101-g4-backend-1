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

class CriarAgendamentoTest extends TesteIntegracao {

    private LocalDate dia;
    private int anaId;
    private int manicureId;
    private ClienteLogado maria;

    @BeforeEach
    void prepararAgendaDaAna() throws Exception {
        dia = LocalDate.now(Banco.FUSO).plusDays(7);

        anaId = criarProfissional(
                "Ana",
                "ana@exemplo.com",
                "segredo123",
                "+5519999990000",
                "ana-unhas");

        manicureId = new ServicoRepositorio(banco)
                .inserir(
                        anaId,
                        "Manicure simples",
                        45,
                        new BigDecimal("35.00"))
                .id();

        inserirFaixa(anaId, dia, "09:00", "12:00");
        inserirFaixa(anaId, dia, "14:00", "18:00");

        maria = criarClienteLogado(
                "Maria Souza",
                "+5519988887777",
                "segredo123");
    }

    private String pedido(int servicoId, String inicio) {
        return "{\"servicoId\":" + servicoId
                + ",\"inicio\":\"" + inicio + "\"}";
    }

    private String as(String hora) {
        return dia + "T" + hora;
    }

    @Test
    void clienteAutenticadoCriaAgendamentoConfirmado() throws Exception {
        HttpResponse<String> r =
                post(
                        "/agendamentos",
                        pedido(manicureId, as("14:00:00")),
                        maria.token());

        assertEquals(201, r.statusCode(), r.body());

        JsonNode corpo = json(r);

        assertEquals(8, corpo.size());
        assertTrue(corpo.get("agendamentoId").asInt() > 0);
        assertEquals(manicureId, corpo.get("servicoId").asInt());
        assertEquals("Manicure simples", corpo.get("servicoNome").asText());
        assertEquals(as("14:00:00"), corpo.get("inicio").asText());
        assertEquals(as("14:45:00"), corpo.get("fim").asText());
        assertEquals(maria.id(), corpo.get("clienteId").asInt());
        assertEquals("Maria Souza", corpo.get("clienteNome").asText());
        assertEquals("confirmado", corpo.get("status").asText());

        try (Connection c = banco.conectar();
             Statement st = c.createStatement();
             ResultSet rs =
                     st.executeQuery("SELECT * FROM agendamento")) {

            assertTrue(rs.next());

            assertEquals(
                    anaId,
                    rs.getInt("profissional_id"));

            assertEquals(
                    manicureId,
                    rs.getInt("servico_id"));

            assertEquals(
                    maria.id(),
                    rs.getInt("cliente_id"));

            assertEquals(
                    LocalDateTime.parse(as("14:00:00")),
                    rs.getObject("inicio", LocalDateTime.class));

            assertEquals(
                    LocalDateTime.parse(as("14:45:00")),
                    rs.getObject("fim", LocalDateTime.class));

            assertEquals(
                    "confirmado",
                    rs.getString("status"));

            assertEquals(
                    "pendente",
                    rs.getString("lembrete_status"));

            assertNull(
                    rs.getObject("lembrete_registrado_em"));
        }
    }

    @Test
    void agendamentoSemLoginEhRecusado() throws Exception {
        assertErro(
                401,
                "NAO_AUTENTICADO",
                post(
                        "/agendamentos",
                        pedido(manicureId, as("14:00:00")),
                        null));
    }

    @Test
    void mesmoClientePodeFazerMaisDeUmAgendamento() throws Exception {
        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(manicureId, as("09:00:00")),
                        maria.token())
                        .statusCode());

        HttpResponse<String> r =
                post(
                        "/agendamentos",
                        pedido(manicureId, as("10:00:00")),
                        maria.token());

        assertEquals(201, r.statusCode(), r.body());

        assertEquals(
                maria.id(),
                json(r).get("clienteId").asInt());

        assertEquals(2, contar("agendamento"));
        assertEquals(1, contar("cliente"));
    }

    @Test
    void aceitaAtendimentoQueTerminaJustoNoFimDaFaixa()
            throws Exception {

        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(manicureId, as("11:15:00")),
                        maria.token())
                        .statusCode());
    }

    @Test
    void horarioCanceladoPodeSerReusado() throws Exception {
        post(
                "/agendamentos",
                pedido(manicureId, as("14:00:00")),
                maria.token());

        try (Connection c = banco.conectar();
             Statement st = c.createStatement()) {

            st.executeUpdate(
                    "UPDATE agendamento SET status = 'cancelado'");
        }

        ClienteLogado joana =
                criarClienteLogado(
                        "Joana",
                        "+5519911112222",
                        "segredo123");

        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(manicureId, as("14:00:00")),
                        joana.token())
                        .statusCode());
    }

    @Test
    void servicoInexistenteDaServicoNaoEncontrado()
            throws Exception {

        assertErro(
                404,
                "SERVICO_NAO_ENCONTRADO",
                post(
                        "/agendamentos",
                        pedido(
                                manicureId + 1000,
                                as("14:00:00")),
                        maria.token()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-13-01T10:00:00",
            "20/10/2026 14:00",
            "2026-10-20",
            "amanhã",
            "",
            "2020-01-06T10:00:00"
    })
    void inicioInvalidoDaDataInvalida(String inicio)
            throws Exception {

        assertErro(
                400,
                "DATA_INVALIDA",
                post(
                        "/agendamentos",
                        pedido(manicureId, inicio),
                        maria.token()));
    }

    @Test
    void inicioAusenteOuComSegundosDaDataInvalida()
            throws Exception {

        assertErro(
                400,
                "DATA_INVALIDA",
                post(
                        "/agendamentos",
                        "{\"servicoId\":" + manicureId + "}",
                        maria.token()));

        assertErro(
                400,
                "DATA_INVALIDA",
                post(
                        "/agendamentos",
                        pedido(
                                manicureId,
                                as("14:00:30")),
                        maria.token()));
    }

    @Test
    void inicioUmMinutoAtrasDaDataInvalida()
            throws Exception {

        String inicio =
                Banco.agora()
                        .minusMinutes(1)
                        .withSecond(0)
                        .withNano(0)
                        .toString();

        assertErro(
                400,
                "DATA_INVALIDA",
                post(
                        "/agendamentos",
                        pedido(manicureId, inicio),
                        maria.token()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "08:30:00",
            "11:30:00",
            "12:30:00",
            "17:30:00",
            "23:30:00"
    })
    void foraDaGradeDaHorarioIndisponivel(String hora)
            throws Exception {

        assertErro(
                409,
                "HORARIO_INDISPONIVEL",
                post(
                        "/agendamentos",
                        pedido(manicureId, as(hora)),
                        maria.token()));
    }

    @Test
    void diaSemGradeDaHorarioIndisponivel()
            throws Exception {

        assertErro(
                409,
                "HORARIO_INDISPONIVEL",
                post(
                        "/agendamentos",
                        pedido(
                                manicureId,
                                dia.plusDays(1)
                                        + "T14:00:00"),
                        maria.token()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "14:30:00",
            "14:00:00",
            "14:45:00",
            "15:00:00"
    })
    void sobrepostoAOutroAgendamentoDaHorarioIndisponivel(
            String hora) throws Exception {

        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(
                                manicureId,
                                as("14:30:00")),
                        maria.token())
                        .statusCode());

        ClienteLogado joana =
                criarClienteLogado(
                        "Joana",
                        "+5519911112222",
                        "segredo123");

        assertErro(
                409,
                "HORARIO_INDISPONIVEL",
                post(
                        "/agendamentos",
                        pedido(manicureId, as(hora)),
                        joana.token()));
    }

    @Test
    void agendamentoDeOutroProfissionalNaoConflita()
            throws Exception {

        int bia = criarProfissional(
                "Bia",
                "bia@exemplo.com",
                "x",
                "+5519977776666",
                "bia-unhas");

        int escova = new ServicoRepositorio(banco)
                .inserir(
                        bia,
                        "Escova",
                        60,
                        new BigDecimal("50.00"))
                .id();

        inserirFaixa(
                bia,
                dia,
                "09:00",
                "18:00");

        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(manicureId, as("14:00:00")),
                        maria.token())
                        .statusCode());

        assertEquals(
                201,
                post(
                        "/agendamentos",
                        pedido(escova, as("14:00:00")),
                        maria.token())
                        .statusCode());
    }

    @Test
    void pedidosSimultaneosNoMesmoHorarioGravamSoUm()
            throws Exception {

        int quantidade = 8;

        List<ClienteLogado> clientes =
                new ArrayList<>();

        for (int i = 0; i < quantidade; i++) {
            clientes.add(
                    criarClienteLogado(
                            "Cliente " + i,
                            "+55199000000" + (10 + i),
                            "segredo123"));
        }

        ExecutorService pool =
                Executors.newFixedThreadPool(quantidade);

        try {
            List<Callable<Integer>> tarefas =
                    new ArrayList<>();

            for (ClienteLogado cliente : clientes) {
                tarefas.add(
                        () -> post(
                                "/agendamentos",
                                pedido(
                                        manicureId,
                                        as("15:00:00")),
                                cliente.token())
                                .statusCode());
            }

            List<Integer> status =
                    new ArrayList<>();

            for (Future<Integer> f :
                    pool.invokeAll(tarefas)) {

                status.add(f.get());
            }

            assertEquals(
                    1,
                    status.stream()
                            .filter(s -> s == 201)
                            .count(),
                    status.toString());

            assertEquals(
                    quantidade - 1,
                    status.stream()
                            .filter(s -> s == 409)
                            .count(),
                    status.toString());

            assertEquals(
                    1,
                    contar("agendamento"));

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void mesmoClientePodeAgendarComDoisProfissionaisAoMesmoTempo()
            throws Exception {

        int bia = criarProfissional(
                "Bia",
                "bia@exemplo.com",
                "x",
                "+5519977776666",
                "bia-unhas");

        int escova = new ServicoRepositorio(banco)
                .inserir(
                        bia,
                        "Escova",
                        60,
                        new BigDecimal("50.00"))
                .id();

        inserirFaixa(
                bia,
                dia,
                "09:00",
                "18:00");

        ExecutorService pool =
                Executors.newFixedThreadPool(2);

        try {
            List<Callable<HttpResponse<String>>> tarefas =
                    List.of(
                            () -> post(
                                    "/agendamentos",
                                    pedido(
                                            manicureId,
                                            as("15:00:00")),
                                    maria.token()),

                            () -> post(
                                    "/agendamentos",
                                    pedido(
                                            escova,
                                            as("15:00:00")),
                                    maria.token()));

            for (Future<HttpResponse<String>> f :
                    pool.invokeAll(tarefas)) {

                HttpResponse<String> r = f.get();

                assertEquals(
                        201,
                        r.statusCode(),
                        r.body());

                assertEquals(
                        maria.id(),
                        json(r)
                                .get("clienteId")
                                .asInt());
            }

            assertEquals(
                    1,
                    contar("cliente"));

            assertEquals(
                    2,
                    contar("agendamento"));

        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"inicio\":\"2030-01-07T10:00:00\"}",
            "{\"servicoId\":\"12\",\"inicio\":\"2030-01-07T10:00:00\"}",
            "{\"servicoId\":1.5,\"inicio\":\"2030-01-07T10:00:00\"}",
            "não é json",
            "[]"
    })
    void corpoMalformadoDaDadosInvalidos(String corpo)
            throws Exception {

        assertErro(
                400,
                "DADOS_INVALIDOS",
                post(
                        "/agendamentos",
                        corpo,
                        maria.token()));
    }

    private void inserirFaixa(
            int profissionalId,
            LocalDate dia,
            String inicio,
            String fim) throws Exception {

        String sql =
                "INSERT INTO horario_atendimento "
                        + "(profissional_id, dia_semana, hora_inicio, hora_fim)"
                        + " VALUES (?, ?, ?, ?)";

        try (Connection c = banco.conectar();
             PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setInt(1, profissionalId);

            ps.setString(
                    2,
                    HorarioAtendimento.diaSemana(
                            dia.getDayOfWeek()));

            ps.setString(3, inicio);
            ps.setString(4, fim);

            ps.executeUpdate();
        }
    }

    private int contar(String tabela) throws Exception {
        try (Connection c = banco.conectar();
             Statement st = c.createStatement();
             ResultSet rs =
                     st.executeQuery(
                             "SELECT COUNT(*) FROM "
                                     + tabela)) {

            rs.next();
            return rs.getInt(1);
        }
    }
}