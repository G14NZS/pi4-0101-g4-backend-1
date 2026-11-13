package br.com.reservice.agendamento;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT11 listarAgendamentosDoDia, lendo as tabelas CT17 (cliente) e CT18 (agendamento). */
class ListarAgendamentosDoDiaTest extends TesteIntegracao {

    private static final String DIA = "2026-10-20";
    private static final String MARIA = "+5519988887777";
    private static final String JOANA = "+5519911112222";

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();
    private final ClienteRepositorio clientes = new ClienteRepositorio();

    private int anaId;
    private int biaId;
    private int manicureId;
    private int escovaId;
    private int pedicureDaBiaId;
    private int mariaId;
    private int joanaId;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        biaId = criarProfissional("Bia", "bia@exemplo.com", "segredo123", "+5519999991111", "bia-cabelos");
        ServicoRepositorio servicos = new ServicoRepositorio(banco);
        manicureId = servicos.inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        escovaId = servicos.inserir(anaId, "Escova", 30, new BigDecimal("50.00")).id();
        pedicureDaBiaId = servicos.inserir(biaId, "Pedicure", 40, new BigDecimal("40.00")).id();
        try (Connection c = banco.conectar()) {
            mariaId = clientes.inserir(c, "Maria Souza", MARIA).id();
            joanaId = clientes.inserir(c, "Joana Lima", JOANA).id();
        }
        token = json(post("/sessoes", "{\"email\":\"ana@exemplo.com\",\"senha\":\"segredo123\"}", null))
                .get("token").asText();
    }

    /** Grava direto no banco: assim dá para usar datas passadas e horários fora da grade. */
    private int agendar(int profissionalId, int servicoId, int clienteId, String inicio, String fim) throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, profissionalId, servicoId, clienteId,
                    LocalDateTime.parse(inicio), LocalDateTime.parse(fim)).id();
        }
    }

    private void cancelar(int agendamentoId) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("UPDATE agendamento SET status = 'cancelado' WHERE id = ?")) {
            ps.setInt(1, agendamentoId);
            ps.executeUpdate();
        }
    }

    private HttpResponse<String> listar(String data, String comToken) throws Exception {
        return get("/agendamentos?data=" + data, comToken);
    }

    @Test
    void devolveSoOsConfirmadosDoProfissionalNoDiaEmOrdemDeHorario() throws Exception {
        // Gravados fora de ordem, para provar a ordenação.
        int tarde = agendar(anaId, manicureId, mariaId, DIA + "T15:00:00", DIA + "T15:45:00");
        int meiaNoite = agendar(anaId, escovaId, joanaId, DIA + "T00:00:00", DIA + "T00:30:00");
        int manha = agendar(anaId, escovaId, joanaId, DIA + "T09:00:00", DIA + "T09:30:00");
        // Não podem aparecer:
        cancelar(agendar(anaId, manicureId, mariaId, DIA + "T10:00:00", DIA + "T10:45:00"));
        agendar(anaId, manicureId, mariaId, "2026-10-19T23:00:00", "2026-10-19T23:45:00");
        agendar(anaId, manicureId, mariaId, "2026-10-21T00:00:00", "2026-10-21T00:45:00");
        agendar(biaId, pedicureDaBiaId, mariaId, DIA + "T11:00:00", DIA + "T11:40:00");

        HttpResponse<String> r = listar(DIA, token);

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(2, corpo.size(), "só os campos da ficha: " + corpo);
        assertEquals(DIA, corpo.get("data").asText());
        JsonNode lista = corpo.get("agendamentos");
        assertEquals(3, lista.size(), lista.toString());
        assertEquals(meiaNoite, lista.get(0).get("agendamentoId").asInt());
        assertEquals(manha, lista.get(1).get("agendamentoId").asInt());
        assertEquals(tarde, lista.get(2).get("agendamentoId").asInt());

        JsonNode item = lista.get(2);
        assertEquals(6, item.size(), "só os campos do AgendamentoDoDia: " + item);
        assertEquals(DIA + "T15:00:00", item.get("inicio").asText());
        assertEquals(DIA + "T15:45:00", item.get("fim").asText());
        assertEquals("Maria Souza", item.get("clienteNome").asText());
        assertEquals(MARIA, item.get("clienteTelefone").asText());
        assertEquals("Manicure simples", item.get("servicoNome").asText());
        assertEquals("Joana Lima", lista.get(1).get("clienteNome").asText());
        assertEquals("Escova", lista.get(1).get("servicoNome").asText());
    }

    @Test
    void diaSemAgendamentosDevolveListaVazia() throws Exception {
        agendar(biaId, pedicureDaBiaId, mariaId, DIA + "T11:00:00", DIA + "T11:40:00");

        HttpResponse<String> r = listar(DIA, token);

        assertEquals(200, r.statusCode(), r.body());
        assertEquals(DIA, json(r).get("data").asText());
        assertTrue(json(r).get("agendamentos").isArray());
        assertEquals(0, json(r).get("agendamentos").size());
    }

    @Test
    void cadaProfissionalVeSoAPropriaAgenda() throws Exception {
        agendar(anaId, manicureId, mariaId, DIA + "T09:00:00", DIA + "T09:45:00");
        int daBia = agendar(biaId, pedicureDaBiaId, joanaId, DIA + "T09:00:00", DIA + "T09:40:00");
        String tokenDaBia = json(post("/sessoes", "{\"email\":\"bia@exemplo.com\",\"senha\":\"segredo123\"}", null))
                .get("token").asText();

        JsonNode lista = json(listar(DIA, tokenDaBia)).get("agendamentos");

        assertEquals(1, lista.size(), lista.toString());
        assertEquals(daBia, lista.get(0).get("agendamentoId").asInt());
    }

    // ---- NAO_AUTENTICADO ----

    @Test
    void tokenAusenteDaNaoAutenticado() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", listar(DIA, null));
    }

    @Test
    void tokenInvalidoDaNaoAutenticado() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", listar(DIA, "nao-e-um-jwt"));
    }

    @Test
    void tokenAssinadoComOutroSegredoDaNaoAutenticado() throws Exception {
        String falso = new Tokens("outro-segredo-qualquer-com-mais-de-32-caracteres", Duration.ofHours(1),
                Clock.systemUTC()).emitir(anaId);
        assertErro(401, "NAO_AUTENTICADO", listar(DIA, falso));
    }

    @Test
    void tokenVencidoDaNaoAutenticado() throws Exception {
        Clock ontem = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
        String vencido = new Tokens(SEGREDO, Duration.ofMinutes(60), ontem).emitir(anaId);
        assertErro(401, "NAO_AUTENTICADO", listar(DIA, vencido));
    }

    @Test
    void tokenEhConferidoAntesDaData() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", listar("20-10-2026", null));
    }

    // ---- DATA_INVALIDA ----

    @ParameterizedTest
    @ValueSource(strings = {
            "",                    // vazia
            "20/10/2026",          // formato brasileiro
            "20-10-2026",          // dia antes do ano
            "2026-10-5",           // sem zero à esquerda
            "2026-13-01",          // mês que não existe
            "2026-02-30",          // dia que não existe
            "2026-10-20T14:00:00", // data e hora
            "amanha"
    })
    void dataForaDoFormatoDaDataInvalida(String data) throws Exception {
        assertErro(400, "DATA_INVALIDA", listar(data, token));
    }

    @Test
    void semParametroDataDaDataInvalida() throws Exception {
        assertErro(400, "DATA_INVALIDA", get("/agendamentos", token));
    }
}
