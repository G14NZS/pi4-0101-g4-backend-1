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

/** Emite e confere os JWTs do Backend 1. */
public class Tokens {

    private static final String EMISSOR = "reservice-backend1";
    private static final String TIPO_PROFISSIONAL = "profissional";
    private static final String TIPO_CLIENTE = "cliente";

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

    /**
     * Mantido com o mesmo nome para não quebrar o login do profissional.
     */
    public String emitir(int profissionalId) {
        return emitir(profissionalId, TIPO_PROFISSIONAL);
    }

    /** Emite token para cliente autenticado. */
    public String emitirCliente(int clienteId) {
        return emitir(clienteId, TIPO_CLIENTE);
    }

    private String emitir(int id, String tipo) {
        Instant agora = relogio.instant();

        return JWT.create()
                .withIssuer(EMISSOR)
                .withSubject(Integer.toString(id))
                .withClaim("tipo", tipo)
                .withIssuedAt(agora)
                .withExpiresAt(agora.plus(validade))
                .sign(algoritmo);
    }

    /** Id do profissional dono do token. */
    public int profissionalAutenticado(Requisicao requisicao) {
        return usuarioAutenticado(requisicao, TIPO_PROFISSIONAL);
    }

    /** Id do cliente dono do token. */
    public int clienteAutenticado(Requisicao requisicao) {
        return usuarioAutenticado(requisicao, TIPO_CLIENTE);
    }

    private int usuarioAutenticado(Requisicao requisicao, String tipoEsperado) {
        String token = requisicao.tokenBearer()
                .orElseThrow(ErroApi::naoAutenticado);

        try {
            DecodedJWT jwt = verificador.verify(token);

            String tipo = jwt.getClaim("tipo").asString();

            if (!tipoEsperado.equals(tipo)) {
                throw ErroApi.naoAutenticado();
            }

            return Integer.parseInt(jwt.getSubject());

        } catch (JWTVerificationException | NumberFormatException e) {
            throw ErroApi.naoAutenticado();
        }
    }
}