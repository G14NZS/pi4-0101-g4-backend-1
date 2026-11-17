package br.com.reservice.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Pedido HTTP já casado com uma rota: parâmetros do caminho e corpo JSON. */
public class Requisicao {

    private static final int TAMANHO_MAXIMO_CORPO = 64 * 1024;

    private final HttpExchange troca;
    private final List<String> parametros;

    Requisicao(HttpExchange troca, List<String> parametros) {
        this.troca = troca;
        this.parametros = parametros;
    }

    /** Parâmetro do caminho na ordem dos grupos da expressão da rota, já decodificado. */
    public String parametro(int indice) {
        return parametros.get(indice);
    }

    /**
     * Valor do parâmetro {@code nome} da query string, já decodificado; vazio quando não veio.
     * Se o parâmetro aparecer mais de uma vez, vale o primeiro.
     */
    public Optional<String> parametroConsulta(String nome) {
        String consulta = troca.getRequestURI().getRawQuery();
        if (consulta == null) {
            return Optional.empty();
        }
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            String chave = igual < 0 ? par : par.substring(0, igual);
            if (decodificar(chave).equals(nome)) {
                return Optional.of(igual < 0 ? "" : decodificar(par.substring(igual + 1)));
            }
        }
        return Optional.empty();
    }

    private static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Escape malformado (ex.: "%zz"): segue com o texto cru, e a validação do campo recusa.
            return texto;
        }
    }

    /** Corpo como objeto JSON; qualquer outra coisa é {@code DADOS_INVALIDOS}. */
    public JsonNode corpoJson() {
        byte[] bytes;
        try (InputStream in = troca.getRequestBody()) {
            bytes = in.readNBytes(TAMANHO_MAXIMO_CORPO + 1);
        } catch (IOException e) {
            throw ErroApi.dadosInvalidos("Não foi possível ler os dados enviados.");
        }
        if (bytes.length > TAMANHO_MAXIMO_CORPO) {
            throw ErroApi.dadosInvalidos("Os dados enviados são grandes demais.");
        }
        try {
            JsonNode corpo = Json.MAPPER.readTree(bytes);
            if (corpo == null || !corpo.isObject()) {
                throw ErroApi.dadosInvalidos("Envie os dados em formato JSON.");
            }
            return corpo;
        } catch (JsonProcessingException e) {
            throw ErroApi.dadosInvalidos("Envie os dados em formato JSON.");
        } catch (IOException e) {
            throw ErroApi.dadosInvalidos("Não foi possível ler os dados enviados.");
        }
    }

    /** Token do cabeçalho {@code Authorization: Bearer {token}}, se houver. */
    public Optional<String> tokenBearer() {
        String cabecalho = troca.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null) {
            return Optional.empty();
        }
        String[] partes = cabecalho.trim().split("\\s+", 2);
        if (partes.length != 2 || !partes[0].equalsIgnoreCase("Bearer") || partes[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(partes[1].trim());
    }
}
