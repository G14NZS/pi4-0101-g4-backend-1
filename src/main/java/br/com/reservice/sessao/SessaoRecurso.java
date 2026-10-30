package br.com.reservice.sessao;

import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.profissional.Profissional;
import br.com.reservice.profissional.ProfissionalRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.SQLException;
import java.util.Optional;

/** CT1 abrirSessao: {@code POST /sessoes}. */
public class SessaoRecurso {

    /** Hash de uma senha qualquer, conferido quando o e-mail não existe para o tempo de resposta não denunciar isso. */
    private static final String HASH_FALSO = BCrypt.hashpw("senha-que-nao-existe", BCrypt.gensalt(10));

    public record SessaoAberta(String token, int profissionalId, String profissionalNome, String profissionalSlug) {
    }

    private final ProfissionalRepositorio profissionais;
    private final Tokens tokens;

    public SessaoRecurso(ProfissionalRepositorio profissionais, Tokens tokens) {
        this.profissionais = profissionais;
        this.tokens = tokens;
    }

    public Resposta abrirSessao(Requisicao requisicao) throws SQLException {
        JsonNode corpo = requisicao.corpoJson();
        String email = textoPreenchido(corpo, "email");
        String senha = textoPreenchido(corpo, "senha");
        if (email == null || senha == null) {
            throw ErroApi.dadosInvalidos("Informe o e-mail e a senha.");
        }

        Optional<Profissional> encontrado = profissionais.buscarPorEmail(email.trim());
        boolean senhaConfere = BCrypt.checkpw(senha, encontrado.map(Profissional::senhaHash).orElse(HASH_FALSO));
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new ErroApi(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos.");
        }

        Profissional p = encontrado.get();
        return Resposta.ok(new SessaoAberta(tokens.emitir(p.id()), p.id(), p.nome(), p.slug()));
    }

    private static String textoPreenchido(JsonNode corpo, String campo) {
        JsonNode valor = corpo.get(campo);
        return valor != null && valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }
}
