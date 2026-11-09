package br.com.reservice.socket;

import br.com.reservice.App;
import br.com.reservice.TesteIntegracao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O {@code accept()} do socket TCP bloqueia, mas numa thread própria: o HTTP não pode parar por causa dele.
 * Usa o servidor HTTP da base {@link TesteIntegracao} (precisa do MySQL de teste) e o socket montado pelo {@link App}.
 */
class ConcorrenciaTest extends TesteIntegracao {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ServidorSocket socket;

    @BeforeAll
    void subirSocket() throws Exception {
        socket = App.montarSocket(config);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            socket.iniciar(0, 2);
        });
    }

    @AfterAll
    void pararSocket() {
        socket.parar();
    }

    @Test
    void iniciarVoltaNaHoraSemPrenderAThreadQueChamou() throws Exception {
        ServidorSocket outro = App.montarSocket(config);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                outro.iniciar(0, 1);
            });
        } finally {
            outro.parar();
        }
    }

    @Test
    void conexaoTcpParadaNaoTravaOHttp() throws Exception {
        try (Socket ociosa = new Socket("localhost", socket.porta())) {
            // A conexão fica aberta, sem enviar nada: ocupa uma thread do pool TCP.
            HttpResponse<String> r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                return get("/profissionais/inexistente/servicos");
            });

            assertEquals(404, r.statusCode(), r.body());
            assertTrue(ociosa.isConnected());
        }
    }

    @Test
    void outraConexaoTcpContinuaSendoAtendidaComUmaOciosaAberta() throws Exception {
        try (Socket ociosa = new Socket("localhost", socket.porta())) {
            JsonNode r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                return ping();
            });

            assertTrue(r.get("ok").asBoolean(), r.toString());
            assertTrue(ociosa.isConnected());
        }
    }

    private JsonNode ping() throws Exception {
        try (Socket s = new Socket("localhost", socket.porta());
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            out.write("{\"operacao\":\"ping\",\"dados\":{},\"chaveInterna\":\"" + config.chaveInterna() + "\"}\n");
            out.flush();
            return JSON.readTree(in.readLine());
        }
    }
}
