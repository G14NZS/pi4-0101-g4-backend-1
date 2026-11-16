package br.com.reservice.lembrete;

import br.com.reservice.App;
import br.com.reservice.TesteIntegracao;
import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.socket.ServidorSocket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CT12 listarLembretesPendentes e CT13 registrarLembrete pelo socket TCP, lendo e gravando a tabela CT18.
 * O socket roda com o relógio parado em {@link #AGORA}, para testar os limites da janela ao segundo.
 */
class LembretesSocketTest extends TesteIntegracao {

    /** "Agora" do Backend 1 nestes testes, no horário de Brasília. */
    private static final LocalDateTime AGORA = LocalDateTime.parse("2026-10-20T11:15:00");
    /** O que o Backend 2 manda: agora mais 3 horas. */
    private static final LocalDateTime ATE = AGORA.plusHours(3);

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();
    private ServidorSocket socket;

    private int anaId;
    private int manicureId;
    private int mariaId;

    @BeforeAll
    void subirSocket() throws Exception {
        Clock parado = Clock.fixed(AGORA.atZone(Banco.FUSO).toInstant(), Banco.FUSO);
        socket = App.montarSocket(config, parado);
        socket.iniciar(0, 4);
    }

    @AfterAll
    void pararSocket() {
        socket.parar();
    }

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        manicureId = new ServicoRepositorio(banco).inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        try (Connection c = banco.conectar()) {
            mariaId = new ClienteRepositorio().inserir(c, "Maria Souza", "+5519988887777").id();
        }
    }

    // ---- apoio ----

    private int agendar(LocalDateTime inicio) throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, anaId, manicureId, mariaId, inicio, inicio.plusMinutes(45)).id();
        }
    }

    private void atualizar(int agendamentoId, String coluna, String valor) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("UPDATE agendamento SET " + coluna + " = ? WHERE id = ?")) {
            ps.setString(1, valor);
            ps.setInt(2, agendamentoId);
            ps.executeUpdate();
        }
    }

    private JsonNode enviar(String mensagem) throws Exception {
        try (Socket s = new Socket("localhost", socket.porta());
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(mensagem + "\n");
            out.flush();
            return json(in.readLine());
        }
    }

    private static JsonNode json(String texto) throws Exception {
        return br.com.reservice.http.Json.MAPPER.readTree(texto);
    }

    private JsonNode operacao(String operacao, String dados) throws Exception {
        return enviar("{\"operacao\":\"" + operacao + "\",\"dados\":" + dados
                + ",\"chaveInterna\":\"" + config.chaveInterna() + "\"}");
    }

    private JsonNode listar(String ate) throws Exception {
        return operacao(LembreteRecurso.LISTAR, "{\"ate\":\"" + ate + "\"}");
    }

    private List<Integer> idsListados(LocalDateTime ate) throws Exception {
        JsonNode r = listar(ate.toString());
        assertTrue(r.path("ok").asBoolean(), r.toString());
        List<Integer> ids = new ArrayList<>();
        r.get("dados").get("lembretes").forEach(l -> ids.add(l.get("agendamentoId").asInt()));
        return ids;
    }

    private JsonNode registrar(Object agendamentoId, String resultado) throws Exception {
        return operacao(LembreteRecurso.REGISTRAR,
                "{\"agendamentoId\":" + agendamentoId + ",\"resultado\":\"" + resultado + "\"}");
    }

    private static void assertErro(String codigo, JsonNode r) {
        assertFalse(r.path("ok").asBoolean(true), r.toString());
        assertEquals(codigo, r.path("erro").asText(), r.toString());
        assertFalse(r.path("mensagem").asText().isBlank(), "mensagem vazia: " + r);
        assertEquals(3, r.size(), "erro deve ter só ok, erro e mensagem: " + r);
    }

    // ================= CT12 listarLembretesPendentes =================

    @Test
    void devolveOsCamposDaFichaEmOrdemDeInicio() throws Exception {
        int depois = agendar(AGORA.plusHours(2));
        int antes = agendar(AGORA.plusMinutes(30));

        JsonNode r = listar(ATE + ":00");

        assertTrue(r.get("ok").asBoolean(), r.toString());
        assertEquals(1, r.get("dados").size(), "só os campos da ficha: " + r);
        JsonNode lista = r.get("dados").get("lembretes");
        assertEquals(2, lista.size(), lista.toString());
        assertEquals(antes, lista.get(0).get("agendamentoId").asInt());
        assertEquals(depois, lista.get(1).get("agendamentoId").asInt());

        JsonNode item = lista.get(0);
        assertEquals(6, item.size(), "só os campos do LembretePendente: " + item);
        assertEquals("Maria Souza", item.get("clienteNome").asText());
        assertEquals("+5519988887777", item.get("clienteTelefone").asText());
        assertEquals("Manicure simples", item.get("servicoNome").asText());
        assertEquals("Ana Beatriz Costa", item.get("profissionalNome").asText());
        assertEquals("2026-10-20T11:45:00", item.get("inicio").asText());
    }

    @Test
    void semNadaNaJanelaDevolveListaVazia() throws Exception {
        agendar(ATE.plusHours(1));

        JsonNode r = listar(ATE + ":00");

        assertTrue(r.get("ok").asBoolean(), r.toString());
        assertTrue(r.get("dados").get("lembretes").isArray());
        assertEquals(0, r.get("dados").get("lembretes").size());
    }

    // ---- limites da janela (agora, ate] ----

    @Test
    void inicioExatamenteEmAteEntra() throws Exception {
        int noLimite = agendar(ATE);
        assertEquals(List.of(noLimite), idsListados(ATE));
    }

    @Test
    void inicioUmSegundoDepoisDeAteNaoEntra() throws Exception {
        agendar(ATE.plusSeconds(1));
        assertEquals(List.of(), idsListados(ATE));
    }

    @Test
    void inicioUmSegundoDepoisDeAgoraEntra() throws Exception {
        int logoDepois = agendar(AGORA.plusSeconds(1));
        assertEquals(List.of(logoDepois), idsListados(ATE));
    }

    @Test
    void inicioExatamenteAgoraNaoEntra() throws Exception {
        agendar(AGORA);
        assertEquals(List.of(), idsListados(ATE));
    }

    @Test
    void inicioNoPassadoNaoEntraMesmoComLembretePendente() throws Exception {
        agendar(AGORA.minusMinutes(1));
        agendar(AGORA.minusDays(1));
        assertEquals(List.of(), idsListados(ATE));
    }

    @Test
    void ateMenorQueAgoraDevolveListaVazia() throws Exception {
        agendar(AGORA.plusMinutes(30));
        assertEquals(List.of(), idsListados(AGORA.minusHours(1)));
        assertEquals(List.of(), idsListados(AGORA));
    }

    @Test
    void ateSemSegundosEhAceito() throws Exception {
        int id = agendar(ATE);
        JsonNode r = listar("2026-10-20T14:15");
        assertTrue(r.get("ok").asBoolean(), r.toString());
        assertEquals(id, r.get("dados").get("lembretes").get(0).get("agendamentoId").asInt());
    }

    // ---- só confirmados com lembrete pendente ----

    @Test
    void canceladoNaoEntra() throws Exception {
        atualizar(agendar(AGORA.plusHours(1)), "status", "cancelado");
        assertEquals(List.of(), idsListados(ATE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"enviado", "falhou"})
    void lembreteJaRegistradoNaoEntra(String lembreteStatus) throws Exception {
        atualizar(agendar(AGORA.plusHours(1)), "lembrete_status", lembreteStatus);
        assertEquals(List.of(), idsListados(ATE));
    }

    @Test
    void depoisDeRegistradoNaoVoltaNaLista() throws Exception {
        int id = agendar(AGORA.plusHours(1));
        assertEquals(List.of(id), idsListados(ATE));

        assertTrue(registrar(id, "enviado").get("ok").asBoolean());

        assertEquals(List.of(), idsListados(ATE));
    }

    @Test
    void semRegistroOLembreteVoltaNoCicloSeguinte() throws Exception {
        // CT13: SMS aceito e registro perdido. O lembrete segue pendente e sai de novo no próximo ciclo.
        int id = agendar(AGORA.plusHours(1));
        assertEquals(List.of(id), idsListados(ATE));
        assertEquals(List.of(id), idsListados(ATE.plusMinutes(15)));
    }

    // ---- ACESSO_NEGADO ----

    @Test
    void semChaveDaAcessoNegado() throws Exception {
        assertErro("ACESSO_NEGADO", enviar("{\"operacao\":\"listarLembretesPendentes\",\"dados\":{\"ate\":\"" + ATE + "\"}}"));
    }

    @Test
    void chaveErradaDaAcessoNegado() throws Exception {
        agendar(AGORA.plusHours(1));
        JsonNode r = enviar("{\"operacao\":\"listarLembretesPendentes\",\"dados\":{\"ate\":\"" + ATE
                + "\"},\"chaveInterna\":\"" + config.chaveInterna() + "x\"}");
        assertErro("ACESSO_NEGADO", r);
    }

    @Test
    void chaveEhConferidaAntesDaData() throws Exception {
        assertErro("ACESSO_NEGADO", enviar("{\"operacao\":\"listarLembretesPendentes\",\"dados\":{\"ate\":\"x\"},"
                + "\"chaveInterna\":\"errada\"}"));
    }

    // ---- DATA_INVALIDA ----

    @ParameterizedTest
    @ValueSource(strings = {"", "2026-10-20", "20/10/2026 12:15", "2026-10-20 12:15:00", "2026-13-01T10:00:00",
            "2026-02-30T10:00:00", "2026-10-20T12:15:00Z", "2026-10-20T12:15:00-03:00", "amanhã"})
    void ateForaDoFormatoDaDataInvalida(String ate) throws Exception {
        assertErro("DATA_INVALIDA", listar(ate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ate\":null}", "{\"ate\":20261020}", "{\"ate\":[]}"})
    void ateAusenteOuNaoTextoDaDataInvalida(String dados) throws Exception {
        assertErro("DATA_INVALIDA", operacao(LembreteRecurso.LISTAR, dados));
    }

    @Test
    void semDadosDaDataInvalida() throws Exception {
        assertErro("DATA_INVALIDA", enviar("{\"operacao\":\"listarLembretesPendentes\",\"chaveInterna\":\""
                + config.chaveInterna() + "\"}"));
    }

    // ================= CT13 registrarLembrete =================

    @ParameterizedTest
    @ValueSource(strings = {"enviado", "falhou"})
    void registraOResultadoEDevolveOsCamposDaFicha(String resultado) throws Exception {
        int id = agendar(AGORA.plusHours(1));

        JsonNode r = registrar(id, resultado);

        assertTrue(r.get("ok").asBoolean(), r.toString());
        JsonNode dados = r.get("dados");
        assertEquals(3, dados.size(), "só os campos da ficha: " + dados);
        assertEquals(id, dados.get("agendamentoId").asInt());
        assertEquals(resultado, dados.get("resultado").asText());
        assertEquals("2026-10-20T11:15:00", dados.get("registradoEm").asText());

        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM agendamento WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(resultado, rs.getString("lembrete_status"));
                assertEquals(AGORA, rs.getObject("lembrete_registrado_em", LocalDateTime.class));
                assertEquals("confirmado", rs.getString("status"), "o status do agendamento não muda");
            }
        }
    }

    // ---- AGENDAMENTO_NAO_ENCONTRADO ----

    @Test
    void agendamentoInexistenteDaAgendamentoNaoEncontrado() throws Exception {
        int id = agendar(AGORA.plusHours(1));
        assertErro("AGENDAMENTO_NAO_ENCONTRADO", registrar(id + 1000, "enviado"));
    }

    // ---- DADOS_INVALIDOS ----

    @ParameterizedTest
    @ValueSource(strings = {"entregue", "pendente", "ENVIADO", " enviado", ""})
    void resultadoForaDaListaDaDadosInvalidos(String resultado) throws Exception {
        int id = agendar(AGORA.plusHours(1));

        assertErro("DADOS_INVALIDOS", registrar(id, resultado));
        assertEquals(List.of(id), idsListados(ATE), "o lembrete continua pendente");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"agendamentoId\":1}",
            "{\"agendamentoId\":1,\"resultado\":null}",
            "{\"agendamentoId\":1,\"resultado\":true}",
            "{\"resultado\":\"enviado\"}",
            "{\"agendamentoId\":\"1\",\"resultado\":\"enviado\"}",
            "{\"agendamentoId\":1.5,\"resultado\":\"enviado\"}",
            "{\"agendamentoId\":99999999999,\"resultado\":\"enviado\"}"
    })
    void camposAusentesOuDeTipoErradoDaDadosInvalidos(String dados) throws Exception {
        assertErro("DADOS_INVALIDOS", operacao(LembreteRecurso.REGISTRAR, dados));
    }

    @Test
    void resultadoInvalidoEmAgendamentoInexistenteDaDadosInvalidos() throws Exception {
        assertErro("DADOS_INVALIDOS", registrar(424242, "entregue"));
    }

    // ---- LEMBRETE_JA_REGISTRADO ----

    @Test
    void registrarDuasVezesDaLembreteJaRegistradoEMantemOPrimeiro() throws Exception {
        int id = agendar(AGORA.plusHours(1));
        assertTrue(registrar(id, "falhou").get("ok").asBoolean());

        assertErro("LEMBRETE_JA_REGISTRADO", registrar(id, "enviado"));

        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT lembrete_status FROM agendamento WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("falhou", rs.getString(1));
            }
        }
    }

    @Test
    void registrosSimultaneosGravamSoUm() throws Exception {
        int id = agendar(AGORA.plusHours(1));
        int pedidos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(pedidos);
        try {
            List<Callable<JsonNode>> tarefas = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                tarefas.add(() -> registrar(id, "enviado"));
            }
            int ok = 0;
            int jaRegistrado = 0;
            for (Future<JsonNode> f : pool.invokeAll(tarefas)) {
                JsonNode r = f.get();
                if (r.get("ok").asBoolean()) {
                    ok++;
                } else if ("LEMBRETE_JA_REGISTRADO".equals(r.get("erro").asText())) {
                    jaRegistrado++;
                }
            }
            assertEquals(1, ok);
            assertEquals(pedidos - 1, jaRegistrado);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- ACESSO_NEGADO ----

    @Test
    void registrarSemChaveDaAcessoNegadoENaoGrava() throws Exception {
        int id = agendar(AGORA.plusHours(1));
        ObjectNode pedido = br.com.reservice.http.Json.MAPPER.createObjectNode();
        pedido.put("operacao", LembreteRecurso.REGISTRAR);
        pedido.putObject("dados").put("agendamentoId", id).put("resultado", "enviado");

        assertErro("ACESSO_NEGADO", enviar(pedido.toString()));
        pedido.put("chaveInterna", "chave-errada-qualquer");
        assertErro("ACESSO_NEGADO", enviar(pedido.toString()));

        assertEquals(List.of(id), idsListados(ATE), "o lembrete continua pendente");
    }

}
