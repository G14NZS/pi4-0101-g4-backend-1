package br.com.reservice.horario;

import br.com.reservice.TesteIntegracao;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT4 definirHorarios. */
class DefinirHorariosTest extends TesteIntegracao {

    private int anaId;
    private String token;

    @BeforeEach
    void entrarComoAna() throws Exception {
        anaId = criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        token = entrar("ana@exemplo.com");
    }

    private String entrar(String email) throws Exception {
        return json(post("/sessoes", "{\"email\":\"" + email + "\",\"senha\":\"segredo123\"}", null)).get("token").asText();
    }

    private HttpResponse<String> put(String corpo, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri("/horarios"))
                .PUT(HttpRequest.BodyPublishers.ofString(corpo))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGEM_FRONTEND);
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return enviar(b);
    }

    private static String faixa(String dia, String inicio, String fim) {
        return "{\"diaSemana\":\"" + dia + "\",\"horaInicio\":\"" + inicio + "\",\"horaFim\":\"" + fim + "\"}";
    }

    private static String grade(String... faixas) {
        return "{\"horarios\":[" + String.join(",", faixas) + "]}";
    }

    private int quantasFaixas(int profissionalId) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM horario_atendimento WHERE profissional_id = ?")) {
            ps.setInt(1, profissionalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void defineAGradeEDevolveOQueGravou() throws Exception {
        HttpResponse<String> r = put(grade(faixa("segunda", "09:00", "18:00"), faixa("sabado", "09:00", "13:00")), token);

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(2, corpo.get("horarios").size());
        assertEquals("segunda", corpo.get("horarios").get(0).get("diaSemana").asText());
        assertTrue(corpo.get("atualizadoEm").asText().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                "data sem fuso: " + corpo.get("atualizadoEm"));
        assertEquals(2, quantasFaixas(anaId));
    }

    @Test
    void substituiAGradeInteira() throws Exception {
        put(grade(faixa("segunda", "09:00", "18:00"), faixa("terca", "09:00", "18:00")), token);

        HttpResponse<String> r = put(grade(faixa("sexta", "10:00", "16:00")), token);

        assertEquals(200, r.statusCode(), r.body());
        assertEquals(1, quantasFaixas(anaId));
    }

    @Test
    void listaVaziaSignificaNaoAtenderEmNenhumDia() throws Exception {
        put(grade(faixa("segunda", "09:00", "18:00")), token);

        HttpResponse<String> r = put(grade(), token);

        assertEquals(200, r.statusCode(), r.body());
        assertEquals(0, json(r).get("horarios").size());
        assertEquals(0, quantasFaixas(anaId));
    }

    @Test
    void faixasColadasNoMesmoDiaSaoAceitas() throws Exception {
        HttpResponse<String> r = put(grade(faixa("segunda", "09:00", "12:00"), faixa("segunda", "12:00", "18:00")), token);

        assertEquals(200, r.statusCode(), r.body());
        assertEquals(2, quantasFaixas(anaId));
    }

    @Test
    void semTokenEhNaoAutenticado() throws Exception {
        assertErro(401, "NAO_AUTENTICADO", put(grade(), null));
        assertErro(401, "NAO_AUTENTICADO", put(grade(), "token-invalido"));
    }

    @Test
    void corpoSemListaEhDadosInvalidos() throws Exception {
        assertErro(400, "DADOS_INVALIDOS", put("{}", token));
        assertErro(400, "DADOS_INVALIDOS", put("{\"horarios\":\"segunda\"}", token));
    }

    @Test
    void diaForaDaListaEhDadosInvalidos() throws Exception {
        assertErro(400, "DADOS_INVALIDOS", put(grade(faixa("segunda-feira", "09:00", "18:00")), token));
        assertErro(400, "DADOS_INVALIDOS", put(grade(faixa("Segunda", "09:00", "18:00")), token));
    }

    @Test
    void horaForaDoFormatoEhHorarioInvalido() throws Exception {
        assertErro(400, "HORARIO_INVALIDO", put(grade(faixa("segunda", "9:00", "18:00")), token));
        assertErro(400, "HORARIO_INVALIDO", put(grade(faixa("segunda", "09:00", "24:00")), token));
        assertErro(400, "HORARIO_INVALIDO", put("{\"horarios\":[{\"diaSemana\":\"segunda\",\"horaInicio\":\"09:00\"}]}", token));
    }

    @Test
    void fimNaoDepoisDoInicioEhHorarioInvalido() throws Exception {
        assertErro(400, "HORARIO_INVALIDO", put(grade(faixa("segunda", "18:00", "09:00")), token));
        assertErro(400, "HORARIO_INVALIDO", put(grade(faixa("segunda", "09:00", "09:00")), token));
    }

    @Test
    void faixasSobrepostasNoMesmoDiaSaoRecusadas() throws Exception {
        assertErro(400, "HORARIOS_SOBREPOSTOS",
                put(grade(faixa("segunda", "09:00", "13:00"), faixa("segunda", "12:00", "18:00")), token));
    }

    @Test
    void mesmoHorarioEmDiasDiferentesNaoEhSobreposicao() throws Exception {
        HttpResponse<String> r = put(grade(faixa("segunda", "09:00", "18:00"), faixa("terca", "09:00", "18:00")), token);

        assertEquals(200, r.statusCode(), r.body());
    }

    @Test
    void pedidoInvalidoNaoApagaAGradeAnterior() throws Exception {
        put(grade(faixa("segunda", "09:00", "18:00")), token);

        assertErro(400, "HORARIOS_SOBREPOSTOS",
                put(grade(faixa("terca", "09:00", "13:00"), faixa("terca", "12:00", "18:00")), token));

        assertEquals(1, quantasFaixas(anaId));
    }

    @Test
    void naoMexeNaGradeDeOutroProfissional() throws Exception {
        int brunoId = criarProfissional("Bruno", "bruno@exemplo.com", "segredo123", "+5519988880000", "bruno-barba");
        put(grade(faixa("segunda", "09:00", "18:00")), entrar("bruno@exemplo.com"));

        assertEquals(200, put(grade(), token).statusCode());

        assertEquals(1, quantasFaixas(brunoId));
        assertEquals(0, quantasFaixas(anaId));
    }

    @Test
    void preflightLiberaPutParaOFrontend() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/horarios"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGEM_FRONTEND)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization, content-type"));

        assertEquals(204, r.statusCode());
        assertTrue(r.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("PUT"));
    }
}
