package br.com.reservice.servico;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT2 cadastrarServico. */
class CadastrarServicoTest extends TesteIntegracao {

    private static final String MANICURE = "{\"servicoNome\":\"Manicure simples\",\"duracaoMinutos\":45,\"preco\":35.00}";

    private int anaId;
    private String token;

    @BeforeEach
    void entrarComoAna() throws Exception {
        anaId = criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        token = json(post("/sessoes", "{\"email\":\"ana@exemplo.com\",\"senha\":\"segredo123\"}", null))
                .get("token").asText();
    }

    @Test
    void cadastraEDevolveOServico() throws Exception {
        HttpResponse<String> r = post("/servicos", MANICURE, token);

        assertEquals(201, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertTrue(corpo.get("servicoId").asInt() > 0);
        assertEquals("Manicure simples", corpo.get("servicoNome").asText());
        assertEquals(45, corpo.get("duracaoMinutos").asInt());
        assertTrue(r.body().contains("\"preco\":35.00"), "preço com duas casas: " + r.body());
        LocalDateTime criadoEm = LocalDateTime.parse(corpo.get("criadoEm").asText());
        assertTrue(corpo.get("criadoEm").asText().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                "data sem fuso: " + corpo.get("criadoEm"));
        LocalDateTime agoraBrasilia = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        assertTrue(Duration.between(criadoEm, agoraBrasilia).abs().toMinutes() < 2, "criadoEm no horário de Brasília");

        JsonNode lista = json(get("/profissionais/ana-unhas/servicos")).get("servicos");
        assertEquals(1, lista.size());
        assertEquals(corpo.get("servicoId").asInt(), lista.get(0).get("servicoId").asInt());
    }

    @Test
    void aceitaLimitesDeDuracaoEPrecoZero() throws Exception {
        assertEquals(201, post("/servicos",
                "{\"servicoNome\":\"Curto\",\"duracaoMinutos\":15,\"preco\":0}", token).statusCode());
        assertEquals(201, post("/servicos",
                "{\"servicoNome\":\"Longo\",\"duracaoMinutos\":480,\"preco\":1234.5}", token).statusCode());
    }

    @Test
    void guardaONomeSemEspacosNasPontas() throws Exception {
        HttpResponse<String> r = post("/servicos",
                "{\"servicoNome\":\"  Pedicure  \",\"duracaoMinutos\":30,\"preco\":30}", token);

        assertEquals("Pedicure", json(r).get("servicoNome").asText());
    }

    // ---- NAO_AUTENTICADO ----

    @Test
    void tokenAusenteDaNaoAutenticado() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", post("/servicos", MANICURE, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Basic YW5hOnNlZ3JlZG8=", "Token abc", "abc"})
    void cabecalhoAuthorizationMalformadoDaNaoAutenticado(String cabecalho) throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/servicos"))
                .POST(HttpRequest.BodyPublishers.ofString(MANICURE))
                .header("Authorization", cabecalho));

        assertErro(401, "NAO_AUTENTICADO", r);
    }

    @Test
    void tokenInvalidoDaNaoAutenticado() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", post("/servicos", MANICURE, "isto.nao.e-um-jwt"));
    }

    @Test
    void tokenAssinadoComOutroSegredoDaNaoAutenticado() throws Exception {
        String falso = new Tokens("outro-segredo-qualquer-com-mais-de-32-caracteres", Duration.ofHours(1),
                Clock.systemUTC()).emitir(anaId);

        assertErro(401, "NAO_AUTENTICADO", post("/servicos", MANICURE, falso));
    }

    @Test
    void tokenAdulteradoDaNaoAutenticado() throws Exception {
        String[] partes = token.split("\\.");
        String outroPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"reservice-backend1\",\"sub\":\"" + (anaId + 1) + "\",\"exp\":4102444800}").getBytes());

        assertErro(401, "NAO_AUTENTICADO",
                post("/servicos", MANICURE, partes[0] + "." + outroPayload + "." + partes[2]));
    }

    @Test
    void tokenVencidoDaNaoAutenticado() throws Exception {
        Clock ontem = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
        String vencido = new Tokens(SEGREDO, Duration.ofMinutes(60), ontem).emitir(anaId);

        assertErro(401, "NAO_AUTENTICADO", post("/servicos", MANICURE, vencido));
    }

    @Test
    void tokenDeProfissionalQueNaoExisteMaisDaNaoAutenticado() throws Exception {
        String orfao = new Tokens(SEGREDO, Duration.ofMinutes(60), Clock.systemUTC()).emitir(anaId + 1000);

        assertErro(401, "NAO_AUTENTICADO", post("/servicos", MANICURE, orfao));
    }

    @Test
    void tokenEhConferidoAntesDosDados() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", post("/servicos", "{}", null));
    }

    // ---- DADOS_INVALIDOS ----

    @ParameterizedTest
    @ValueSource(strings = {
            // servicoNome vazio
            "{\"duracaoMinutos\":45,\"preco\":35}",
            "{\"servicoNome\":\"\",\"duracaoMinutos\":45,\"preco\":35}",
            "{\"servicoNome\":\"   \",\"duracaoMinutos\":45,\"preco\":35}",
            "{\"servicoNome\":null,\"duracaoMinutos\":45,\"preco\":35}",
            "{\"servicoNome\":12,\"duracaoMinutos\":45,\"preco\":35}",
            // duracaoMinutos fora de 15 a 480
            "{\"servicoNome\":\"X\",\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":14,\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":481,\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":0,\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":-30,\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45.5,\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":\"45\",\"preco\":35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":99999999999,\"preco\":35}",
            // preco negativo (e preço ausente ou que não é número)
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45,\"preco\":-0.01}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45,\"preco\":-35}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45,\"preco\":\"35,00\"}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45,\"preco\":35.001}",
            "{\"servicoNome\":\"X\",\"duracaoMinutos\":45,\"preco\":100000000}",
            // corpo que não é objeto JSON
            "não é json",
            "[]",
            ""
    })
    void dadosForaDaFichaDaoDadosInvalidos(String corpo) throws Exception {
        assertErro(400, "DADOS_INVALIDOS", post("/servicos", corpo, token));
    }

    @Test
    void nomeMaiorQueAColunaDaDadosInvalidos() throws Exception {
        String nome = "a".repeat(ServicoRecurso.NOME_TAMANHO_MAXIMO + 1);

        assertErro(400, "DADOS_INVALIDOS", post("/servicos",
                "{\"servicoNome\":\"" + nome + "\",\"duracaoMinutos\":45,\"preco\":35}", token));
    }

    // ---- SERVICO_DUPLICADO ----

    @Test
    void mesmoNomeDoMesmoProfissionalDaServicoDuplicado() throws Exception {
        assertEquals(201, post("/servicos", MANICURE, token).statusCode());

        assertErro(409, "SERVICO_DUPLICADO", post("/servicos", MANICURE, token));
    }

    @Test
    void nomeIgualComOutraCaixaOuEspacosTambemEhDuplicado() throws Exception {
        assertEquals(201, post("/servicos", MANICURE, token).statusCode());

        assertErro(409, "SERVICO_DUPLICADO", post("/servicos",
                "{\"servicoNome\":\"  MANICURE SIMPLES \",\"duracaoMinutos\":30,\"preco\":20}", token));
    }

    @Test
    void mesmoNomeEmOutroProfissionalPode() throws Exception {
        assertEquals(201, post("/servicos", MANICURE, token).statusCode());
        criarProfissional("Bia", "bia@exemplo.com", "outra123", "+5519988887777", "bia-unhas");
        String tokenBia = json(post("/sessoes", "{\"email\":\"bia@exemplo.com\",\"senha\":\"outra123\"}", null))
                .get("token").asText();

        assertEquals(201, post("/servicos", MANICURE, tokenBia).statusCode());
    }
}
