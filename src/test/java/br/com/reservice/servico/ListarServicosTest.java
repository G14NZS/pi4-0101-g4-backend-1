package br.com.reservice.servico;

import br.com.reservice.TesteIntegracao;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT3 listarServicos. */
class ListarServicosTest extends TesteIntegracao {

    @Test
    void listaOsServicosDoProfissionalSemLogin() throws Exception {
        int ana = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        int bia = criarProfissional("Bia", "bia@exemplo.com", "outra123", "+5519988887777", "bia-unhas");
        ServicoRepositorio servicos = new ServicoRepositorio(banco);
        servicos.inserir(ana, "Pedicure", 40, new BigDecimal("30.00"));
        servicos.inserir(ana, "Manicure simples", 45, new BigDecimal("35.00"));
        servicos.inserir(bia, "Escova", 60, new BigDecimal("50.00"));

        HttpResponse<String> r = get("/profissionais/ana-unhas/servicos");

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals("Ana Beatriz Costa", corpo.get("profissionalNome").asText());
        assertEquals("+5519999990000", corpo.get("profissionalTelefone").asText());
        JsonNode lista = corpo.get("servicos");
        assertEquals(2, lista.size(), "só os serviços da Ana");
        JsonNode primeiro = lista.get(0);
        assertEquals("Manicure simples", primeiro.get("servicoNome").asText());
        assertEquals(45, primeiro.get("duracaoMinutos").asInt());
        assertTrue(primeiro.get("servicoId").asInt() > 0);
        assertTrue(r.body().contains("\"preco\":35.00"), r.body());
        assertEquals(4, primeiro.size(), "objeto Servico tem só os campos da ficha: " + primeiro);
    }

    @Test
    void profissionalSemServicosDevolveListaVazia() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");

        JsonNode corpo = json(get("/profissionais/ana-unhas/servicos"));

        assertTrue(corpo.get("servicos").isArray());
        assertEquals(0, corpo.get("servicos").size());
    }

    @Test
    void slugInexistenteDaProfissionalNaoEncontrado() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");

        assertErro(404, "PROFISSIONAL_NAO_ENCONTRADO", get("/profissionais/ninguem/servicos"));
    }

    @Test
    void slugComCaracteresCodificadosNaoQuebra() throws Exception {
        assertErro(404, "PROFISSIONAL_NAO_ENCONTRADO", get("/profissionais/a%20b%27%22/servicos"));
    }
}
