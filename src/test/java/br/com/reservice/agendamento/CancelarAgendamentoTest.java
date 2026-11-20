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

/**
 * CT9 cancelarAgendamento.
 *
 * Na H05 o cliente é identificado pelo JWT, e não mais
 * pelo telefone enviado no corpo da requisição.
 */
class CancelarAgendamentoTest extends TesteIntegracao {

    private static final String MARIA =
            "+5519988887777";

    private static final String JOANA =
            "+5519911112222";

    private static final String SENHA =
            "segredo123";

    private final AgendamentoRepositorio agendamentos =
            new AgendamentoRepositorio();

    private int anaId;
    private int manicureId;

    private int mariaId;

    private String tokenMaria;
    private String tokenJoana;

    private LocalDateTime semanaQueVem;

    private int agendamentoDaMaria;

    @BeforeEach
    void preparar() throws Exception {

        anaId = criarProfissional(
                "Ana",
                "ana@exemplo.com",
                "x",
                "+5519999990000",
                "ana-unhas");

        manicureId =
                new ServicoRepositorio(banco)
                        .inserir(
                                anaId,
                                "Manicure simples",
                                45,
                                new BigDecimal("35.00"))
                        .id();

        ClienteLogado maria =
                criarClienteLogado(
                        "Maria Souza",
                        MARIA,
                        SENHA);

        ClienteLogado joana =
                criarClienteLogado(
                        "Joana Lima",
                        JOANA,
                        SENHA);

        mariaId = maria.id();
        tokenMaria = maria.token();
        tokenJoana = joana.token();

        semanaQueVem =
                Banco.agora()
                        .plusDays(7)
                        .withHour(14)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);

        agendamentoDaMaria =
                agendar(semanaQueVem);
    }

    /**
     * Grava direto no banco para permitir inclusive
     * datas passadas nos testes.
     */
    private int agendar(
            LocalDateTime inicio) throws Exception {

        try (Connection c = banco.conectar()) {

            return agendamentos
                    .inserir(
                            c,
                            anaId,
                            manicureId,
                            mariaId,
                            inicio,
                            inicio.plusMinutes(45))
                    .id();
        }
    }

    private HttpResponse<String> cancelar(
            Object agendamentoId,
            String token) throws Exception {

        return patch(
                "/agendamentos/"
                        + agendamentoId
                        + "/cancelamento",
                "{}",
                token);
    }

    private String status(
            int agendamentoId) throws Exception {

        try (Connection c = banco.conectar();
             PreparedStatement ps =
                     c.prepareStatement(
                             "SELECT status FROM agendamento WHERE id = ?")) {

            ps.setInt(
                    1,
                    agendamentoId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                rs.next();

                return rs.getString(1);
            }
        }
    }

    @Test
    void cancelaOAgendamentoFuturoDoProprioCliente()
            throws Exception {

        LocalDateTime antes =
                Banco.agora();

        HttpResponse<String> r =
                cancelar(
                        agendamentoDaMaria,
                        tokenMaria);

        assertEquals(
                200,
                r.statusCode(),
                r.body());

        JsonNode corpo =
                json(r);

        assertEquals(
                3,
                corpo.size());

        assertEquals(
                agendamentoDaMaria,
                corpo.get("agendamentoId").asInt());

        assertEquals(
                "cancelado",
                corpo.get("status").asText());

        LocalDateTime atualizadoEm =
                LocalDateTime.parse(
                        corpo.get("atualizadoEm").asText());

        assertTrue(
                !atualizadoEm.isBefore(antes)
                        && !atualizadoEm.isAfter(Banco.agora()));

        try (Connection c = banco.conectar();
             PreparedStatement ps =
                     c.prepareStatement(
                             "SELECT * FROM agendamento WHERE id = ?")) {

            ps.setInt(
                    1,
                    agendamentoDaMaria);

            try (ResultSet rs =
                         ps.executeQuery()) {

                rs.next();

                assertEquals(
                        "cancelado",
                        rs.getString("status"));

                assertEquals(
                        atualizadoEm,
                        rs.getObject(
                                "atualizado_em",
                                LocalDateTime.class));

                assertEquals(
                        semanaQueVem,
                        rs.getObject(
                                "inicio",
                                LocalDateTime.class));
            }
        }
    }

    @Test
    void agendamentoCanceladoSaiDaListaDoCliente()
            throws Exception {

        cancelar(
                agendamentoDaMaria,
                tokenMaria);

        HttpResponse<String> resposta =
                get(
                        "/agendamentos/do-cliente",
                        tokenMaria);

        assertEquals(
                200,
                resposta.statusCode(),
                resposta.body());

        JsonNode lista =
                json(resposta)
                        .get("agendamentos");

        assertEquals(
                0,
                lista.size());
    }

    @Test
    void horarioCanceladoFicaLivreParaOutroAgendamento()
            throws Exception {

        try (Connection c = banco.conectar();
             PreparedStatement ps =
                     c.prepareStatement(
                             "INSERT INTO horario_atendimento "
                                     + "(profissional_id, dia_semana, hora_inicio, hora_fim) "
                                     + "VALUES (?, ?, '09:00', '18:00')")) {

            ps.setInt(
                    1,
                    anaId);

            ps.setString(
                    2,
                    HorarioAtendimento.diaSemana(
                            semanaQueVem.getDayOfWeek()));

            ps.executeUpdate();
        }

        String pedidoDaJoana =
                "{\"servicoId\":"
                        + manicureId
                        + ",\"inicio\":\""
                        + semanaQueVem
                        + "\"}";

        assertErro(
                409,
                "HORARIO_INDISPONIVEL",
                post(
                        "/agendamentos",
                        pedidoDaJoana,
                        tokenJoana));

        assertEquals(
                200,
                cancelar(
                        agendamentoDaMaria,
                        tokenMaria)
                        .statusCode());

        HttpResponse<String> r =
                post(
                        "/agendamentos",
                        pedidoDaJoana,
                        tokenJoana);

        assertEquals(
                201,
                r.statusCode(),
                r.body());
    }

    // ---------- AUTENTICAÇÃO ----------

    @Test
    void semTokenDaNaoAutenticado()
            throws Exception {

        assertErro(
                401,
                "NAO_AUTENTICADO",
                cancelar(
                        agendamentoDaMaria,
                        null));

        assertEquals(
                "confirmado",
                status(agendamentoDaMaria));
    }

    @Test
    void tokenDeOutroClienteNaoPodeCancelar()
            throws Exception {

        assertErro(
                404,
                "AGENDAMENTO_NAO_ENCONTRADO",
                cancelar(
                        agendamentoDaMaria,
                        tokenJoana));

        assertEquals(
                "confirmado",
                status(agendamentoDaMaria));
    }

    @Test
    void naoRevelaSeAgendamentoExisteParaOutroCliente()
            throws Exception {

        HttpResponse<String> deOutro =
                cancelar(
                        agendamentoDaMaria,
                        tokenJoana);

        HttpResponse<String> inexistente =
                cancelar(
                        agendamentoDaMaria + 1000,
                        tokenJoana);

        assertEquals(
                inexistente.statusCode(),
                deOutro.statusCode());

        assertEquals(
                inexistente.body(),
                deOutro.body());
    }

    // ---------- AGENDAMENTO_NAO_ENCONTRADO ----------

    @Test
    void agendamentoInexistenteDaAgendamentoNaoEncontrado()
            throws Exception {

        assertErro(
                404,
                "AGENDAMENTO_NAO_ENCONTRADO",
                cancelar(
                        agendamentoDaMaria + 1000,
                        tokenMaria));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "-1",
            "1.5",
            "99999999999999999999"
    })
    void idInvalidoNaoRepresentaAgendamento(
            String id) throws Exception {

        assertErro(
                404,
                "AGENDAMENTO_NAO_ENCONTRADO",
                cancelar(
                        id,
                        tokenMaria));
    }

    // ---------- AGENDAMENTO_JA_CANCELADO ----------

    @Test
    void cancelarDuasVezesDaAgendamentoJaCancelado()
            throws Exception {

        assertEquals(
                200,
                cancelar(
                        agendamentoDaMaria,
                        tokenMaria)
                        .statusCode());

        assertErro(
                409,
                "AGENDAMENTO_JA_CANCELADO",
                cancelar(
                        agendamentoDaMaria,
                        tokenMaria));
    }

    @Test
    void canceladoQueJaPassouDaAgendamentoJaCancelado()
            throws Exception {

        int passado =
                agendar(
                        Banco.agora()
                                .minusDays(1));

        try (Connection c = banco.conectar()) {

            agendamentos.cancelar(
                    c,
                    passado,
                    Banco.agora()
                            .minusDays(2));
        }

        assertErro(
                409,
                "AGENDAMENTO_JA_CANCELADO",
                cancelar(
                        passado,
                        tokenMaria));
    }

    @Test
    void cancelamentosSimultaneosCancelamSoUmaVez()
            throws Exception {

        int pedidos = 6;

        ExecutorService pool =
                Executors.newFixedThreadPool(
                        pedidos);

        try {
            List<Callable<Integer>> tarefas =
                    new ArrayList<>();

            for (int i = 0;
                 i < pedidos;
                 i++) {

                tarefas.add(
                        () -> cancelar(
                                agendamentoDaMaria,
                                tokenMaria)
                                .statusCode());
            }

            List<Integer> status =
                    new ArrayList<>();

            for (Future<Integer> f :
                    pool.invokeAll(tarefas)) {

                status.add(
                        f.get());
            }

            assertEquals(
                    1,
                    status.stream()
                            .filter(s -> s == 200)
                            .count(),
                    status.toString());

            assertEquals(
                    pedidos - 1,
                    status.stream()
                            .filter(s -> s == 409)
                            .count(),
                    status.toString());

        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- AGENDAMENTO_JA_PASSOU ----------

    @Test
    void agendamentoDeOntemDaAgendamentoJaPassou()
            throws Exception {

        int ontem =
                agendar(
                        Banco.agora()
                                .minusDays(1));

        assertErro(
                409,
                "AGENDAMENTO_JA_PASSOU",
                cancelar(
                        ontem,
                        tokenMaria));

        assertEquals(
                "confirmado",
                status(ontem));
    }

    @Test
    void agendamentoQueJaComecouDaAgendamentoJaPassou()
            throws Exception {

        int emAndamento =
                agendar(
                        Banco.agora()
                                .minus(
                                        Duration.ofMinutes(10)));

        assertErro(
                409,
                "AGENDAMENTO_JA_PASSOU",
                cancelar(
                        emAndamento,
                        tokenMaria));

        assertEquals(
                "confirmado",
                status(emAndamento));
    }

    @Test
    void agendamentoQueComecaDaquiAUmMinutoAindaPodeSerCancelado()
            throws Exception {

        int quaseNaHora =
                agendar(
                        Banco.agora()
                                .plusMinutes(1)
                                .withSecond(0)
                                .withNano(0));

        assertEquals(
                200,
                cancelar(
                        quaseNaHora,
                        tokenMaria)
                        .statusCode());
    }
}