package br.com.reservice.agendamento;

import br.com.reservice.App;
import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.http.Json;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.socket.ServidorSocket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT12 listarLembretesPendentes e CT13 registrarLembrete, pelo socket de verdade. */
class LembretesSocketTest extends TesteIntegracao {

    private static final String MARIA = "+5519988887777";

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();
    private ServidorSocket socket;

    private int anaId;
    private int manicureId;
    private int mariaId;
    private LocalDateTime agora;

    @BeforeAll
    void subirSocket() throws Exception {
        socket = App.montarSocket(config);
        socket.iniciar(0, 2);
    }

    @AfterAll
    void descerSocket() {
        socket.parar();
    }

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        manicureId = new ServicoRepositorio(banco).inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        try (Connection c = banco.conectar()) {
            mariaId = new ClienteRepositorio().inserir(c, "Maria Souza", MARIA, "hash-de-teste").id();
        }
        agora = Banco.agora().truncatedTo(ChronoUnit.MINUTES);
    }

    private int agendar(LocalDateTime inicio) throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, anaId, manicureId, mariaId, inicio, inicio.plusMinutes(45)).id();
        }
    }

    private void executar(String sql, int id) throws Exception {
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private String lembreteStatus(int id) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT lembrete_status FROM agendamento WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private JsonNode chamar(String operacao, ObjectNode dados) throws Exception {
        ObjectNode pedido = Json.MAPPER.createObjectNode()
                .put("operacao", operacao)
                .put("chaveInterna", config.chaveInterna());
        pedido.set("dados", dados);
        try (Socket s = new Socket("localhost", socket.porta());
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(pedido + "\n");
            out.flush();
            return Json.MAPPER.readTree(in.readLine());
        }
    }

    private JsonNode listar(LocalDateTime ate) throws Exception {
        return chamar("listarLembretesPendentes", Json.MAPPER.createObjectNode().put("ate", ate.toString()));
    }

    private JsonNode registrar(int id, String resultado) throws Exception {
        return chamar("registrarLembrete",
                Json.MAPPER.createObjectNode().put("agendamentoId", id).put("resultado", resultado));
    }

    @Test
    void listaSoConfirmadosPendentesDentroDaJanela() throws Exception {
        int daquiAUmaHora = agendar(agora.plusHours(1));
        int daquiADuas = agendar(agora.plusHours(2));
        // Não podem aparecer:
        agendar(agora.plusHours(5));                                   // fora da janela
        agendar(agora.minusHours(1));                                  // já passou
        int cancelado = agendar(agora.plusMinutes(90));
        executar("UPDATE agendamento SET status = 'cancelado' WHERE id = ?", cancelado);
        int jaEnviado = agendar(agora.plusMinutes(100));
        executar("UPDATE agendamento SET lembrete_status = 'enviado' WHERE id = ?", jaEnviado);

        JsonNode r = listar(agora.plusHours(3));

        assertTrue(r.get("ok").asBoolean(), r.toString());
        JsonNode lembretes = r.get("dados").get("lembretes");
        assertEquals(2, lembretes.size(), r.toString());
        JsonNode primeiro = lembretes.get(0);
        assertEquals(daquiAUmaHora, primeiro.get("agendamentoId").asInt());
        assertEquals("Maria Souza", primeiro.get("clienteNome").asText());
        assertEquals(MARIA, primeiro.get("clienteTelefone").asText());
        assertEquals("Manicure simples", primeiro.get("servicoNome").asText());
        assertEquals("Ana Beatriz Costa", primeiro.get("profissionalNome").asText());
        assertEquals(agora.plusHours(1), LocalDateTime.parse(primeiro.get("inicio").asText()));
        assertEquals(daquiADuas, lembretes.get(1).get("agendamentoId").asInt());
    }

    @Test
    void ateForaDoFormatoDevolveDataInvalida() throws Exception {
        JsonNode r = chamar("listarLembretesPendentes", Json.MAPPER.createObjectNode().put("ate", "amanhã"));

        assertFalse(r.get("ok").asBoolean());
        assertEquals("DATA_INVALIDA", r.get("erro").asText());
    }

    @Test
    void registraEnvioUmaVezSo() throws Exception {
        int id = agendar(agora.plusHours(1));

        JsonNode r = registrar(id, "enviado");

        assertTrue(r.get("ok").asBoolean(), r.toString());
        assertEquals(id, r.get("dados").get("agendamentoId").asInt());
        assertEquals("enviado", r.get("dados").get("resultado").asText());
        LocalDateTime.parse(r.get("dados").get("registradoEm").asText());
        assertEquals("enviado", lembreteStatus(id));
        assertEquals(0, listar(agora.plusHours(3)).get("dados").get("lembretes").size());

        JsonNode deNovo = registrar(id, "falhou");
        assertFalse(deNovo.get("ok").asBoolean());
        assertEquals("LEMBRETE_JA_REGISTRADO", deNovo.get("erro").asText());
        assertEquals("enviado", lembreteStatus(id));
    }

    @Test
    void registraFalha() throws Exception {
        int id = agendar(agora.plusHours(1));

        assertTrue(registrar(id, "falhou").get("ok").asBoolean());
        assertEquals("falhou", lembreteStatus(id));
    }

    @Test
    void agendamentoInexistenteOuResultadoInvalido() throws Exception {
        JsonNode inexistente = registrar(999_999, "enviado");
        assertEquals("AGENDAMENTO_NAO_ENCONTRADO", inexistente.get("erro").asText());

        int id = agendar(agora.plusHours(1));
        JsonNode invalido = registrar(id, "pendente");
        assertEquals("DADOS_INVALIDOS", invalido.get("erro").asText());
        assertEquals("pendente", lembreteStatus(id));
    }
}
