package br.com.reservice.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H01.3: troca de mensagem JSON por socket TCP, com a chave errada recusada. Não precisa de banco. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServidorSocketTest {

    private static final String CHAVE = "chave-interna-de-teste-123456";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ServidorSocket servidor;

    @BeforeAll
    void subir() throws Exception {
        servidor = new ServidorSocket(CHAVE)
                .operacao("eco", dados -> dados)
                .operacao("falhar", dados -> {
                    throw new ServidorSocket.ErroSocket("DADOS_INVALIDOS", "Dados inválidos.");
                });
        servidor.iniciar(0, 2);
    }

    @AfterAll
    void descer() {
        servidor.parar();
    }

    private JsonNode enviar(String mensagem) throws Exception {
        try (Socket s = new Socket("localhost", servidor.porta());
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(mensagem + "\n");
            out.flush();
            return JSON.readTree(in.readLine());
        }
    }

    @Test
    void pingComChaveCertaResponde() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"ping\",\"dados\":{},\"chaveInterna\":\"" + CHAVE + "\"}");

        assertTrue(r.get("ok").asBoolean());
        assertTrue(r.get("dados").get("pong").asBoolean());
    }

    @Test
    void operacaoRecebeEDevolveOsDados() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"eco\",\"dados\":{\"ate\":\"2026-10-20T12:15:00\"},\"chaveInterna\":\"" + CHAVE + "\"}");

        assertTrue(r.get("ok").asBoolean());
        assertEquals("2026-10-20T12:15:00", r.get("dados").get("ate").asText());
    }

    @Test
    void chaveErradaEhRecusada() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"ping\",\"dados\":{},\"chaveInterna\":\"chave-errada\"}");

        assertFalse(r.get("ok").asBoolean());
        assertEquals("ACESSO_NEGADO", r.get("erro").asText());
        assertFalse(r.path("mensagem").asText().isEmpty());
    }

    @Test
    void chaveAusenteEhRecusada() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"ping\",\"dados\":{}}");

        assertFalse(r.get("ok").asBoolean());
        assertEquals("ACESSO_NEGADO", r.get("erro").asText());
    }

    @Test
    void chaveErradaNaoRevelaSeAOperacaoExiste() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"naoExiste\",\"dados\":{},\"chaveInterna\":\"errada\"}");

        assertEquals("ACESSO_NEGADO", r.get("erro").asText());
    }

    @Test
    void operacaoDesconhecidaEhRecusada() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"naoExiste\",\"dados\":{},\"chaveInterna\":\"" + CHAVE + "\"}");

        assertFalse(r.get("ok").asBoolean());
        assertEquals("OPERACAO_DESCONHECIDA", r.get("erro").asText());
    }

    @Test
    void erroDeNegocioVoltaComSeuCodigo() throws Exception {
        JsonNode r = enviar("{\"operacao\":\"falhar\",\"dados\":{},\"chaveInterna\":\"" + CHAVE + "\"}");

        assertFalse(r.get("ok").asBoolean());
        assertEquals("DADOS_INVALIDOS", r.get("erro").asText());
    }

    @Test
    void jsonInvalidoEhRecusadoSemDerrubarOServidor() throws Exception {
        JsonNode r = enviar("isso não é json");
        assertEquals("JSON_INVALIDO", r.get("erro").asText());

        JsonNode depois = enviar("{\"operacao\":\"ping\",\"dados\":{},\"chaveInterna\":\"" + CHAVE + "\"}");
        assertTrue(depois.get("ok").asBoolean());
    }
}
