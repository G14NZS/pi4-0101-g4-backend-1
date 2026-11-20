package br.com.reservice.cliente;

import br.com.reservice.banco.Banco;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

public class ClienteRecurso {

    private static final String HASH_FALSO =
            BCrypt.hashpw("senha-que-nao-existe", BCrypt.gensalt(10));

    public record ClienteCadastrado(
            int clienteId,
            String nome,
            String telefone) {
    }

    public record SessaoCliente(
            String token,
            int clienteId,
            String clienteNome) {
    }

    private final Banco banco;
    private final ClienteRepositorio clientes;
    private final Tokens tokens;

    public ClienteRecurso(
            Banco banco,
            ClienteRepositorio clientes,
            Tokens tokens) {

        this.banco = banco;
        this.clientes = clientes;
        this.tokens = tokens;
    }

    public Resposta cadastrar(Requisicao requisicao) throws SQLException {
        JsonNode corpo = requisicao.corpoJson();

        String nome = textoPreenchido(corpo, "nome");
        String telefone = textoPreenchido(corpo, "telefone");
        String senha = textoPreenchido(corpo, "senha");

        if (nome == null || telefone == null || senha == null) {
            throw ErroApi.dadosInvalidos(
                    "Informe o nome, o telefone e a senha.");
        }

        telefone = telefone.trim();

        if (!ClienteRepositorio.telefoneValido(telefone)) {
            throw new ErroApi(
                    400,
                    "TELEFONE_INVALIDO",
                    "Informe o telefone no formato +55 com DDD.");
        }

        String senhaHash =
                BCrypt.hashpw(senha, BCrypt.gensalt(10));

        try (Connection c = banco.conectar()) {

            Cliente cliente;

            try {
                cliente = clientes.inserir(
                        c,
                        nome.trim(),
                        telefone,
                        senhaHash);

            } catch (SQLIntegrityConstraintViolationException e) {

                if (e.getErrorCode() == Banco.ERRO_VALOR_DUPLICADO) {
                    throw new ErroApi(
                            409,
                            "TELEFONE_JA_CADASTRADO",
                            "Já existe um cliente cadastrado com esse telefone.");
                }

                throw e;
            }

            return Resposta.criado(
                    new ClienteCadastrado(
                            cliente.id(),
                            cliente.nome(),
                            cliente.telefone()));
        }
    }

    public Resposta entrar(Requisicao requisicao) throws SQLException {
        JsonNode corpo = requisicao.corpoJson();

        String telefone = textoPreenchido(corpo, "telefone");
        String senha = textoPreenchido(corpo, "senha");

        if (telefone == null || senha == null) {
            throw ErroApi.dadosInvalidos(
                    "Informe o telefone e a senha.");
        }

        try (Connection c = banco.conectar()) {

            Optional<Cliente> encontrado =
                    clientes.buscarPorTelefone(c, telefone.trim());

            boolean senhaConfere = BCrypt.checkpw(
                    senha,
                    encontrado
                            .map(Cliente::senhaHash)
                            .orElse(HASH_FALSO));

            if (encontrado.isEmpty() || !senhaConfere) {
                throw new ErroApi(
                        401,
                        "CREDENCIAIS_INVALIDAS",
                        "Telefone ou senha incorretos.");
            }

            Cliente cliente = encontrado.get();

            return Resposta.ok(
                    new SessaoCliente(
                            tokens.emitirCliente(cliente.id()),
                            cliente.id(),
                            cliente.nome()));
        }
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