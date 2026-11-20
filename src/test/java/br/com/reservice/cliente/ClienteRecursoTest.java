package br.com.reservice.cliente;

import br.com.reservice.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteRecursoTest extends TesteIntegracao {

    @Test
    void clientePodeSeCadastrar() throws Exception {
        String corpo = """
                {
                  "nome": "Maria Souza",
                  "telefone": "+5519988887777",
                  "senha": "segredo123"
                }
                """;

        HttpResponse<String> r =
                post("/clientes", corpo, null);

        assertEquals(201, r.statusCode(), r.body());
        assertTrue(json(r).get("clienteId").asInt() > 0);
        assertEquals("Maria Souza", json(r).get("nome").asText());
        assertEquals("+5519988887777", json(r).get("telefone").asText());

        try (Connection c = banco.conectar();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT senha_hash FROM cliente")) {

            assertTrue(rs.next());

            String hash = rs.getString("senha_hash");

            assertFalse(hash.equals("segredo123"));
            assertTrue(BCrypt.checkpw("segredo123", hash));
        }
    }

    @Test
    void telefoneDuplicadoEhRecusado() throws Exception {
        criarClienteLogado(
                "Maria",
                "+5519988887777",
                "segredo123");

        String corpo = """
                {
                  "nome": "Outra Maria",
                  "telefone": "+5519988887777",
                  "senha": "outrasenha"
                }
                """;

        assertErro(
                409,
                "TELEFONE_JA_CADASTRADO",
                post("/clientes", corpo, null));
    }

    @Test
    void telefoneInvalidoEhRecusado() throws Exception {
        String corpo = """
                {
                  "nome": "Maria",
                  "telefone": "19988887777",
                  "senha": "segredo123"
                }
                """;

        assertErro(
                400,
                "TELEFONE_INVALIDO",
                post("/clientes", corpo, null));
    }

    @Test
    void clientePodeEntrarComTelefoneESenha() throws Exception {
        criarClienteLogado(
                "Maria",
                "+5519988887777",
                "segredo123");

        String corpo = """
                {
                  "telefone": "+5519988887777",
                  "senha": "segredo123"
                }
                """;

        HttpResponse<String> r =
                post("/clientes/sessoes", corpo, null);

        assertEquals(200, r.statusCode(), r.body());
        assertFalse(json(r).get("token").asText().isBlank());
        assertTrue(json(r).get("clienteId").asInt() > 0);
        assertEquals("Maria", json(r).get("clienteNome").asText());
    }

    @Test
    void senhaErradaEhRecusada() throws Exception {
        criarClienteLogado(
                "Maria",
                "+5519988887777",
                "segredo123");

        String corpo = """
                {
                  "telefone": "+5519988887777",
                  "senha": "senha-errada"
                }
                """;

        assertErro(
                401,
                "CREDENCIAIS_INVALIDAS",
                post("/clientes/sessoes", corpo, null));
    }

    @Test
    void telefoneNaoCadastradoEhRecusado() throws Exception {
        String corpo = """
                {
                  "telefone": "+5519988887777",
                  "senha": "segredo123"
                }
                """;

        assertErro(
                401,
                "CREDENCIAIS_INVALIDAS",
                post("/clientes/sessoes", corpo, null));
    }
}