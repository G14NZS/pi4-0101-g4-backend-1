package br.com.reservice.agendamento;

import br.com.reservice.App;
import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.http.Json;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.socket.ServidorSocket;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT12 listarLembretesPendentes e CT13 registrarLembrete, pelo socket TCP, com banco de verdade. */
class LembreteRecursoTest extends TesteIntegracao {

    private static final String CHAVE = "chave-interna-de-teste-123456";
    private static final String MARIA = "+5519988887777";

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();

    private ServidorSocket socket;
    private int anaId;
    private int manicureId;
    private int mariaId;

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        manicureId = new ServicoRepositorio(banco).inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        try (Connection c = banco.conectar()) {
            mariaId = new ClienteRepositorio().inserir(c, "Maria Souza", MARIA).id();
        }
        socket = App.montarSocket(config);
        socket.iniciar(0, 2);
    }

    @AfterEach
    void encerrar() {
        socket.parar();
    }

    private int agendar(LocalDateTime inicio) throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, anaId, manicureId, mariaId, inicio, inicio.plusMinutes(45)).id();
        }
    }

    private JsonNode chamar(String operacao, String dados, String chave) throws Exception {
        try (Socket s = new Socket("localhost", socket.porta());
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write("{\"operacao\":\"" + operacao + "\",\"dados\":" + dados
                    + ",\"chaveInterna\":\"" + chave + "\"}\n");
            out.flush();
            return Json.MAPPER.readTree(in.readLine());
        }
    }

    private JsonNode listar(LocalDateTime ate) throws Exception {
        return chamar("listarLembretesPendentes", "{\"ate\":\"" + ate + "\"}", CHAVE);
    }

    private JsonNode registrar(int id, String resultado) throws Exception {
        return chamar("registrarLembrete", "{\"agendamentoId\":" + id + ",\"resultado\":\"" + resultado + "\"}", CHAVE);
    }

    private String statusDoLembrete(int id) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT lembrete_status FROM agendamento WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    void listaSoOsConfirmadosPendentesDentroDaJanela() throws Exception {
        LocalDateTime agora = Banco.agora();
        int dentro = agendar(agora.plusHours(2));
        int fora = agendar(agora.plusHours(5));
        int passado = agendar(agora.minusHours(1));
        int cancelado = agendar(agora.plusHours(1));
        try (Connection c = banco.conectar()) {
            agendamentos.cancelar(c, cancelado, agora);
        }

        JsonNode r = listar(agora.plusHours(3));

        assertTrue(r.path("ok").asBoolean(), r.toString());
        JsonNode lista = r.path("dados").path("lembretes");
        assertEquals(1, lista.size(), r.toString());
        JsonNode l = lista.get(0);
        assertEquals(dentro, l.path("agendamentoId").asInt());
        assertEquals("Maria Souza", l.path("clienteNome").asText());
        assertEquals(MARIA, l.path("clienteTelefone").asText());
        assertEquals("Manicure simples", l.path("servicoNome").asText());
        assertEquals("Ana Beatriz Costa", l.path("profissionalNome").asText());
        assertEquals(agora.plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), l.path("inicio").asText());
        assertEquals(6, l.size());
        assertTrue(fora > 0 && passado > 0);
    }

    @Test
    void listaVaziaQuandoNaoHaNada() throws Exception {
        JsonNode r = listar(Banco.agora().plusHours(3));

        assertTrue(r.path("ok").asBoolean(), r.toString());
        assertTrue(r.path("dados").path("lembretes").isArray());
        assertEquals(0, r.path("dados").path("lembretes").size());
    }

    @Test
    void lembreteRegistradoNaoVoltaNaLista() throws Exception {
        int id = agendar(Banco.agora().plusHours(1));

        assertTrue(registrar(id, "enviado").path("ok").asBoolean());

        assertEquals(0, listar(Banco.agora().plusHours(3)).path("dados").path("lembretes").size());
    }

    @Test
    void ateInvalidoOuAusenteDaDataInvalida() throws Exception {
        for (String dados : new String[] {"{}", "{\"ate\":\"amanhã\"}", "{\"ate\":123}"}) {
            JsonNode r = chamar("listarLembretesPendentes", dados, CHAVE);
            assertFalse(r.path("ok").asBoolean(), dados);
            assertEquals("DATA_INVALIDA", r.path("erro").asText(), dados);
        }
    }

    @Test
    void registraEnviadoEFalhou() throws Exception {
        int a = agendar(Banco.agora().plusHours(1));
        int b = agendar(Banco.agora().plusHours(2));

        JsonNode r = registrar(a, "enviado");
        assertTrue(r.path("ok").asBoolean(), r.toString());
        assertEquals(a, r.path("dados").path("agendamentoId").asInt());
        assertEquals("enviado", r.path("dados").path("resultado").asText());
        assertTrue(r.path("dados").path("registradoEm").isTextual());
        assertTrue(registrar(b, "falhou").path("ok").asBoolean());

        assertEquals("enviado", statusDoLembrete(a));
        assertEquals("falhou", statusDoLembrete(b));
    }

    @Test
    void registrarDeNovoDaLembreteJaRegistradoESemMudarONada() throws Exception {
        int id = agendar(Banco.agora().plusHours(1));
        registrar(id, "enviado");

        JsonNode r = registrar(id, "falhou");

        assertFalse(r.path("ok").asBoolean());
        assertEquals("LEMBRETE_JA_REGISTRADO", r.path("erro").asText());
        assertEquals("enviado", statusDoLembrete(id));
    }

    @Test
    void agendamentoInexistente() throws Exception {
        JsonNode r = registrar(999999, "enviado");

        assertFalse(r.path("ok").asBoolean());
        assertEquals("AGENDAMENTO_NAO_ENCONTRADO", r.path("erro").asText());
    }

    @Test
    void dadosInvalidosNoRegistro() throws Exception {
        int id = agendar(Banco.agora().plusHours(1));
        String[] ruins = {
                "{}",
                "{\"agendamentoId\":" + id + "}",
                "{\"agendamentoId\":" + id + ",\"resultado\":\"pendente\"}",
                "{\"agendamentoId\":\"" + id + "\",\"resultado\":\"enviado\"}",
                "{\"agendamentoId\":0,\"resultado\":\"enviado\"}",
                "{\"agendamentoId\":1.5,\"resultado\":\"enviado\"}",
        };
        for (String dados : ruins) {
            JsonNode r = chamar("registrarLembrete", dados, CHAVE);
            assertFalse(r.path("ok").asBoolean(), dados);
            assertEquals("DADOS_INVALIDOS", r.path("erro").asText(), dados);
        }
        assertEquals("pendente", statusDoLembrete(id));
    }

    @Test
    void chaveErradaNaoEnxergaNada() throws Exception {
        int id = agendar(Banco.agora().plusHours(1));

        JsonNode lista = chamar("listarLembretesPendentes", "{\"ate\":\"" + Banco.agora().plusHours(3) + "\"}", "errada");
        JsonNode registro = chamar("registrarLembrete",
                "{\"agendamentoId\":" + id + ",\"resultado\":\"enviado\"}", "errada");

        assertEquals("ACESSO_NEGADO", lista.path("erro").asText());
        assertEquals("ACESSO_NEGADO", registro.path("erro").asText());
        assertEquals("pendente", statusDoLembrete(id));
    }
}
