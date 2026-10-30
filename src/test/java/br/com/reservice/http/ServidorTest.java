package br.com.reservice.http;

import br.com.reservice.TesteIntegracao;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combinados gerais: CORS para o frontend e erro sempre como { erro, mensagem }. */
class ServidorTest extends TesteIntegracao {

    @Test
    void preflightLiberaOFrontendComAuthorization() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/servicos"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGEM_FRONTEND)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization, content-type"));

        assertEquals(204, r.statusCode());
        assertEquals(ORIGEM_FRONTEND, r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        String cabecalhos = r.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase();
        assertTrue(cabecalhos.contains("authorization") && cabecalhos.contains("content-type"), cabecalhos);
        String metodos = r.headers().firstValue("Access-Control-Allow-Methods").orElse("");
        assertTrue(metodos.contains("POST") && metodos.contains("GET"), metodos);
    }

    @Test
    void respostaDeErroTambemLevaCorsParaOFrontendLerOCorpo() throws Exception {
        HttpResponse<String> r = post("/servicos", "{}", null);

        assertErro(401, "NAO_AUTENTICADO", r);
        assertEquals(ORIGEM_FRONTEND, r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void origemNaoLiberadaNaoRecebeCors() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/profissionais/x/servicos"))
                .header("Origin", "http://malicioso.exemplo"));

        assertTrue(r.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void rotaInexistenteVoltaNoFormatoDeErro() throws Exception {
        assertErro(404, "ROTA_NAO_ENCONTRADA", get("/nada"));
    }

    @Test
    void metodoErradoVoltaNoFormatoDeErro() throws Exception {
        assertErro(405, "METODO_NAO_PERMITIDO", get("/sessoes"));
    }

    @Test
    void respostaEhJsonUtf8() throws Exception {
        HttpResponse<String> r = get("/nada");

        assertEquals("application/json; charset=utf-8", r.headers().firstValue("Content-Type").orElse(null));
        assertTrue(r.body().contains("Endereço"), "acentos preservados: " + r.body());
    }
}
