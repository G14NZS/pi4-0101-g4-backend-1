package br.com.reservice.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servidor HTTP do Backend 1: cada conexão é atendida por uma thread de um pool fixo (ADR 0002).
 * Faz o roteamento, o CORS e transforma toda falha em {@code { "erro", "mensagem" }}.
 */
public class Servidor {

    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());

    @FunctionalInterface
    public interface Tratador {
        Resposta tratar(Requisicao requisicao) throws Exception;
    }

    private record Rota(String metodo, Pattern caminho, Tratador tratador) {
    }

    private final List<Rota> rotas = new ArrayList<>();
    private final Set<String> corsOrigens;
    private HttpServer http;
    private ExecutorService threads;

    public Servidor(Set<String> corsOrigens) {
        this.corsOrigens = corsOrigens;
    }

    /** Registra uma rota; {@code caminho} é uma expressão regular cujos grupos viram parâmetros. */
    public Servidor rota(String metodo, String caminho, Tratador tratador) {
        rotas.add(new Rota(metodo, Pattern.compile(caminho), tratador));
        return this;
    }

    public void iniciar(int porta, int quantidadeThreads) throws IOException {
        threads = Executors.newFixedThreadPool(quantidadeThreads);
        http = HttpServer.create(new InetSocketAddress(porta), 0);
        http.createContext("/", this::atender);
        http.setExecutor(threads);
        http.start();
    }

    /** Porta efetiva (útil quando se inicia na porta 0, como nos testes). */
    public int porta() {
        return http.getAddress().getPort();
    }

    public void parar() {
        http.stop(0);
        threads.shutdownNow();
    }

    private void atender(HttpExchange troca) throws IOException {
        try (troca) {
            aplicarCors(troca);
            String metodo = troca.getRequestMethod();
            if (metodo.equals("OPTIONS")) {
                troca.sendResponseHeaders(204, -1);
                return;
            }
            Resposta resposta;
            try {
                resposta = rotear(troca, metodo);
            } catch (ErroApi e) {
                resposta = new Resposta(e.status(), Map.of("erro", e.codigo(), "mensagem", e.getMessage()));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Falha ao atender " + metodo + " " + troca.getRequestURI(), e);
                resposta = new Resposta(500, Map.of(
                        "erro", "ERRO_INTERNO",
                        "mensagem", "Algo deu errado. Tente de novo em instantes."));
            }
            escrever(troca, resposta);
        }
    }

    private Resposta rotear(HttpExchange troca, String metodo) throws Exception {
        String caminho = troca.getRequestURI().getPath();
        boolean caminhoExiste = false;
        for (Rota rota : rotas) {
            Matcher m = rota.caminho().matcher(caminho);
            if (!m.matches()) {
                continue;
            }
            caminhoExiste = true;
            if (rota.metodo().equals(metodo)) {
                List<String> parametros = new ArrayList<>();
                for (int i = 1; i <= m.groupCount(); i++) {
                    parametros.add(m.group(i));
                }
                return rota.tratador().tratar(new Requisicao(troca, parametros));
            }
        }
        if (caminhoExiste) {
            throw new ErroApi(405, "METODO_NAO_PERMITIDO", "Operação não permitida neste endereço.");
        }
        throw new ErroApi(404, "ROTA_NAO_ENCONTRADA", "Endereço não encontrado.");
    }

    private void aplicarCors(HttpExchange troca) {
        String origem = troca.getRequestHeaders().getFirst("Origin");
        Headers h = troca.getResponseHeaders();
        h.add("Vary", "Origin");
        if (origem != null && corsOrigens.contains(origem)) {
            h.set("Access-Control-Allow-Origin", origem);
            h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            h.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            h.set("Access-Control-Max-Age", "600");
        }
    }

    private void escrever(HttpExchange troca, Resposta resposta) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(resposta.corpo());
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(resposta.status(), bytes.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(bytes);
        }
    }
}
