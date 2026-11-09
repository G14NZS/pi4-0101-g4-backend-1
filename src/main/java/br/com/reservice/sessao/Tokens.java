package br.com.reservice.sessao;

import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Emite e confere o JWT do CT1 (HS256, com {@code sub} = id do profissional e {@code exp}). */
public class Tokens {

    private static final String EMISSOR = "reservice-backend1";

    private final Algorithm algoritmo;
    private final Duration validade;
    private final Clock relogio;
    private final JWTVerifier verificador;

    public Tokens(String segredo, Duration validade, Clock relogio) {
        this.algoritmo = Algorithm.HMAC256(segredo);
        this.validade = validade;
        this.relogio = relogio;
        this.verificador = ((JWTVerifier.BaseVerification) JWT.require(algoritmo)
                .withIssuer(EMISSOR)
                .withClaimPresence("exp"))
                .build(relogio);
    }

    public String emitir(int profissionalId) {
        Instant agora = relogio.instant();
        return JWT.create()
                .withIssuer(EMISSOR)
                .withSubject(Integer.toString(profissionalId))
                .withIssuedAt(agora)
                .withExpiresAt(agora.plus(validade))
                .sign(algoritmo);
    }

    /** Id do profissional dono do token; ausente, inválido ou vencido vira {@code NAO_AUTENTICADO}. */
    public int profissionalAutenticado(Requisicao requisicao) {
        String token = requisicao.tokenBearer().orElseThrow(ErroApi::naoAutenticado);
        try {
            DecodedJWT jwt = verificador.verify(token);
            return Integer.parseInt(jwt.getSubject());
        } catch (JWTVerificationException | NumberFormatException e) {
            throw ErroApi.naoAutenticado();
        }
    }
}
