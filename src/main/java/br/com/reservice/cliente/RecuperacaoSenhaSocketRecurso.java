package br.com.reservice.cliente;

import br.com.reservice.banco.Banco;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import br.com.reservice.http.Json;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class RecuperacaoSenhaSocketRecurso {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Banco banco;
    private final RecuperacaoSenhaRepositorio recuperacoes;

    public RecuperacaoSenhaSocketRecurso(
            Banco banco,
            RecuperacaoSenhaRepositorio recuperacoes) {

        this.banco = banco;
        this.recuperacoes = recuperacoes;
    }

    public JsonNode buscarPendente(JsonNode dados)
            throws SQLException {

        try (Connection c = banco.conectar()) {

            c.setAutoCommit(false);

            try {
                var pendente =
                        recuperacoes.buscarPendenteParaEnvio(c);

                if (pendente.isEmpty()) {
                    c.commit();

                    return Json.MAPPER
                            .createObjectNode()
                            .put("disponivel", false);
                }

                String codigo =
                        String.format(
                                "%06d",
                                RANDOM.nextInt(1_000_000));

                String codigoHash =
                        BCrypt.hashpw(
                                codigo,
                                BCrypt.gensalt(10));

                LocalDateTime expiraEm =
                        Banco.agora().plusMinutes(10);

                recuperacoes.definirCodigo(
                        c,
                        pendente.get().id(),
                        codigoHash,
                        expiraEm);

                c.commit();

                ObjectNode resposta =
                        Json.MAPPER.createObjectNode();

                resposta.put("disponivel", true);
                resposta.put(
                        "recuperacaoId",
                        pendente.get().id());

                resposta.put(
                        "telefone",
                        pendente.get().telefone());

                resposta.put(
                        "codigo",
                        codigo);

                resposta.put(
                        "expiraEm",
                        expiraEm.toString());

                return resposta;

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public JsonNode registrarEnvio(JsonNode dados)
            throws SQLException {

        int recuperacaoId =
                dados.path("recuperacaoId").asInt(0);

        boolean sucesso =
                dados.path("sucesso").asBoolean(false);

        String erro =
                dados.path("erro").isTextual()
                        ? dados.path("erro").asText()
                        : null;

        if (recuperacaoId <= 0) {
            throw new IllegalArgumentException(
                    "recuperacaoId inválido");
        }

        try (Connection c = banco.conectar()) {
            recuperacoes.registrarEnvio(
                    c,
                    recuperacaoId,
                    sucesso,
                    erro);
        }

        return Json.MAPPER
                .createObjectNode()
                .put("registrado", true);
    }
}