package br.com.reservice.sessao;

import br.com.reservice.TesteIntegracao;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT1 abrirSessao. */
class AbrirSessaoTest extends TesteIntegracao {

    @Test
    void devolveTokenComExpEDadosDoProfissional() throws Exception {
        int id = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");

        HttpResponse<String> r = post("/sessoes", "{\"email\":\"ana@exemplo.com\",\"senha\":\"segredo123\"}", null);

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(id, corpo.get("profissionalId").asInt());
        assertEquals("Ana Beatriz Costa", corpo.get("profissionalNome").asText());
        assertEquals("ana-unhas", corpo.get("profissionalSlug").asText());

        DecodedJWT jwt = JWT.decode(corpo.get("token").asText());
        assertEquals(Integer.toString(id), jwt.getSubject());
        assertNotNull(jwt.getExpiresAtAsInstant(), "o frontend precisa do exp para voltar ao login");
        Duration restante = Duration.between(Instant.now(), jwt.getExpiresAtAsInstant());
        assertTrue(restante.compareTo(Duration.ofMinutes(59)) > 0 && restante.compareTo(Duration.ofMinutes(61)) < 0,
                "exp deveria ser daqui a JWT_VALIDADE_MINUTOS (60): " + restante);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"senha\":\"segredo123\"}",
            "{\"email\":\"ana@exemplo.com\"}",
            "{\"email\":\"\",\"senha\":\"segredo123\"}",
            "{\"email\":\"ana@exemplo.com\",\"senha\":\"   \"}",
            "{\"email\":null,\"senha\":null}",
            "{\"email\":123,\"senha\":\"segredo123\"}",
            "{}",
            "[]",
            "não é json",
            ""
    })
    void emailOuSenhaNaoEnviadosDaDadosInvalidos(String corpo) throws Exception {
        assertErro(400, "DADOS_INVALIDOS", post("/sessoes", corpo, null));
    }

    @Test
    void emailNaoCadastradoDaCredenciaisInvalidas() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");

        assertErro(401, "CREDENCIAIS_INVALIDAS",
                post("/sessoes", "{\"email\":\"outra@exemplo.com\",\"senha\":\"segredo123\"}", null));
    }

    @Test
    void senhaErradaDaCredenciaisInvalidas() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");

        assertErro(401, "CREDENCIAIS_INVALIDAS",
                post("/sessoes", "{\"email\":\"ana@exemplo.com\",\"senha\":\"errada\"}", null));
    }
}
