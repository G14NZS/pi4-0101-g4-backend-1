package br.com.reservice.cliente;

import br.com.reservice.banco.Banco;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import com.fasterxml.jackson.databind.JsonNode;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

public class RecuperacaoSenhaRecurso {

    private record SolicitacaoAceita(String mensagem) {
    }

    private record SenhaAlterada(String mensagem) {
    }

    private final Banco banco;
    private final ClienteRepositorio clientes;
    private final RecuperacaoSenhaRepositorio recuperacoes;

    public RecuperacaoSenhaRecurso(
            Banco banco,
            ClienteRepositorio clientes,
            RecuperacaoSenhaRepositorio recuperacoes) {

        this.banco = banco;
        this.clientes = clientes;
        this.recuperacoes = recuperacoes;
    }

    public Resposta solicitar(Requisicao requisicao)
            throws SQLException {

        JsonNode corpo = requisicao.corpoJson();
        String telefone = textoPreenchido(corpo, "telefone");

        if (!ClienteRepositorio.telefoneValido(telefone)) {
            throw new ErroApi(
                    400,
                    "TELEFONE_INVALIDO",
                    "Informe um telefone válido.");
        }

        try (Connection c = banco.conectar()) {

            Optional<Cliente> cliente =
                    clientes.buscarPorTelefone(c, telefone.trim());

            /*
             * Resposta propositalmente igual mesmo quando o telefone
             * não está cadastrado, para não revelar contas existentes.
             */
            if (cliente.isPresent()) {
                recuperacoes.inserirPendente(
                        c,
                        cliente.get().id());
            }

            return Resposta.ok(
                    new SolicitacaoAceita(
                            "Se o telefone estiver cadastrado, enviaremos um código de recuperação."));
        }
    }

    public Resposta confirmar(Requisicao requisicao)
            throws SQLException {

        JsonNode corpo = requisicao.corpoJson();

        String telefone =
                textoPreenchido(corpo, "telefone");

        String codigo =
                textoPreenchido(corpo, "codigo");

        String novaSenha =
                textoPreenchido(corpo, "novaSenha");

        if (telefone == null
                || codigo == null
                || novaSenha == null) {

            throw ErroApi.dadosInvalidos(
                    "Informe o telefone, o código e a nova senha.");
        }

        try (Connection c = banco.conectar()) {

            c.setAutoCommit(false);

            try {
                Cliente cliente =
                        clientes.buscarPorTelefone(
                                        c,
                                        telefone.trim())
                                .orElseThrow(
                                        RecuperacaoSenhaRecurso::codigoInvalido);

                RecuperacaoSenha recuperacao =
                        recuperacoes.buscarValidaMaisRecente(
                                        c,
                                        cliente.id())
                                .orElseThrow(
                                        RecuperacaoSenhaRecurso::codigoInvalido);

                LocalDateTime agora = Banco.agora();

                if (recuperacao.expiraEm() == null
                        || !recuperacao.expiraEm().isAfter(agora)
                        || !BCrypt.checkpw(
                                codigo,
                                recuperacao.codigoHash())) {

                    throw codigoInvalido();
                }

                String novaSenhaHash =
                        BCrypt.hashpw(
                                novaSenha,
                                BCrypt.gensalt(10));

                clientes.atualizarSenha(
                        c,
                        cliente.id(),
                        novaSenhaHash);

                recuperacoes.marcarUtilizada(
                        c,
                        recuperacao.id());

                c.commit();

                return Resposta.ok(
                        new SenhaAlterada(
                                "Senha alterada com sucesso."));

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static ErroApi codigoInvalido() {
        return new ErroApi(
                400,
                "CODIGO_INVALIDO",
                "O código é inválido ou expirou.");
    }

    private static String textoPreenchido(
            JsonNode corpo,
            String campo) {

        JsonNode valor = corpo.get(campo);

        return valor != null
                && valor.isTextual()
                && !valor.asText().isBlank()
                ? valor.asText()
                : null;
    }
}