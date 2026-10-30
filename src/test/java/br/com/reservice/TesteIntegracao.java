package br.com.reservice;

import br.com.reservice.banco.Banco;
import br.com.reservice.http.Json;
import br.com.reservice.http.Servidor;
import br.com.reservice.profissional.ProfissionalRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base dos testes: sobe o servidor de verdade numa porta livre, apontando para um MySQL de teste
 * (variáveis TESTE_DB_*; ver o serviço {@code testes} do docker-compose.yml), e recria o esquema.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TesteIntegracao {

    protected static final String SEGREDO = "segredo-de-teste-com-pelo-menos-32-caracteres";
    protected static final String ORIGEM_FRONTEND = "http://localhost:5173";

    protected Config config;
    protected Banco banco;
    protected ProfissionalRepositorio profissionais;
    private Servidor servidor;
    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeAll
    void subir() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", System.getenv().getOrDefault("TESTE_DB_URL",
                "jdbc:mysql://localhost:3306/reservice_teste?createDatabaseIfNotExist=true"));
        env.put("DB_USUARIO", System.getenv().getOrDefault("TESTE_DB_USUARIO", "root"));
        env.put("DB_SENHA", System.getenv().getOrDefault("TESTE_DB_SENHA", "root"));
        env.put("JWT_SEGREDO", SEGREDO);
        env.put("JWT_VALIDADE_MINUTOS", "60");
        env.put("CORS_ORIGENS", ORIGEM_FRONTEND);
        config = Config.de(env);

        banco = new Banco(config.bancoUrl(), config.bancoUsuario(), config.bancoSenha());
        profissionais = new ProfissionalRepositorio(banco);
        recriarEsquema();

        servidor = App.montar(config, Clock.systemUTC());
        servidor.iniciar(0, 4);
    }

    @AfterAll
    void descer() {
        servidor.parar();
    }

    @BeforeEach
    void limparTabelas() throws Exception {
        try (Connection c = banco.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM servico");
            st.executeUpdate("DELETE FROM profissional");
        }
    }

    private void recriarEsquema() throws Exception {
        String schema = Files.readString(Path.of("db", "schema.sql"));
        try (Connection c = banco.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS servico");
            st.executeUpdate("DROP TABLE IF EXISTS profissional");
            for (String comando : schema.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!comando.isBlank()) {
                    st.executeUpdate(comando);
                }
            }
        }
    }

    protected int criarProfissional(String nome, String email, String senha, String telefone, String slug)
            throws Exception {
        return profissionais.inserir(nome, email, BCrypt.hashpw(senha, BCrypt.gensalt(4)), telefone, slug);
    }

    protected HttpResponse<String> get(String caminho) throws Exception {
        return enviar(HttpRequest.newBuilder(uri(caminho)).GET().header("Origin", ORIGEM_FRONTEND));
    }

    protected HttpResponse<String> post(String caminho, String corpoJson, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(caminho))
                .POST(HttpRequest.BodyPublishers.ofString(corpoJson))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGEM_FRONTEND);
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return enviar(b);
    }

    protected HttpResponse<String> enviar(HttpRequest.Builder pedido) throws Exception {
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String caminho) {
        return URI.create("http://localhost:" + servidor.porta() + caminho);
    }

    protected static JsonNode json(HttpResponse<String> resposta) throws Exception {
        return Json.MAPPER.readTree(resposta.body());
    }

    /** Confere status, código de erro e que a mensagem veio preenchida (formato da visão geral). */
    protected static void assertErro(int status, String codigo, HttpResponse<String> resposta) throws Exception {
        assertEquals(status, resposta.statusCode(), resposta.body());
        JsonNode corpo = json(resposta);
        assertEquals(codigo, corpo.path("erro").asText(), resposta.body());
        assertEquals(true, corpo.path("mensagem").isTextual() && !corpo.path("mensagem").asText().isBlank(),
                "mensagem vazia: " + resposta.body());
        assertEquals(2, corpo.size(), "corpo de erro deve ter só erro e mensagem: " + resposta.body());
    }
}
