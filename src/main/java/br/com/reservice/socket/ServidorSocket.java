package br.com.reservice.socket;

import br.com.reservice.http.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor de socket TCP do Backend 1 para o Backend 2 (CT12 e CT13).
 * Protocolo: uma mensagem JSON por linha. Pedido: {@code {"operacao":..., "dados":{...}, "chaveInterna":...}}.
 * Resposta de sucesso: {@code {"ok":true,"dados":{...}}}; de erro: {@code {"ok":false,"erro":CODIGO,"mensagem":texto}}.
 *
 * <p>Concorrência: {@code accept()} bloqueia, então roda numa thread própria ({@code socket-aceitador}) e
 * {@link #iniciar} volta na hora, sem prender a thread principal nem o servidor HTTP. Cada conexão aceita é
 * atendida por um pool fixo, separado do pool do HTTP; com o pool e a fila cheios, a conexão nova é fechada.
 */
public final class ServidorSocket {

    private static final Logger LOG = Logger.getLogger(ServidorSocket.class.getName());
    private static final int TIMEOUT_LEITURA_MS = 10_000;
    /** Conexões esperando uma thread livre; passou disso, a conexão nova é fechada (não cresce sem limite). */
    private static final int FILA_MAXIMA = 64;

    /** Uma operação atendida pelo socket. Lance {@link ErroSocket} para devolver um erro de negócio. */
    @FunctionalInterface
    public interface Operacao {
        JsonNode executar(JsonNode dados) throws Exception;
    }

    /** Erro de negócio com código em MAIÚSCULAS_COM_SUBLINHADO. */
    public static final class ErroSocket extends RuntimeException {
        private final String codigo;

        public ErroSocket(String codigo, String mensagem) {
            super(mensagem);
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }
    }

    private final byte[] chaveInterna;
    private final Map<String, Operacao> operacoes = new HashMap<>();
    private ServerSocket servidor;
    private ExecutorService threads;
    private Thread aceitador;

    public ServidorSocket(String chaveInterna) {
        if (chaveInterna == null || chaveInterna.isBlank()) {
            throw new IllegalArgumentException("chaveInterna obrigatória");
        }
        this.chaveInterna = chaveInterna.getBytes(StandardCharsets.UTF_8);
        // Operação de verificação de conexão; as demais (CT12, CT13) são registradas pelo App.
        operacoes.put("ping", dados -> Json.MAPPER.createObjectNode().put("pong", true));
    }

    public ServidorSocket operacao(String nome, Operacao operacao) {
        operacoes.put(nome, operacao);
        return this;
    }

    public void iniciar(int porta, int quantidadeThreads) throws IOException {
        threads = new ThreadPoolExecutor(quantidadeThreads, quantidadeThreads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(FILA_MAXIMA));
        servidor = new ServerSocket(porta);
        aceitador = new Thread(this::aceitar, "socket-aceitador");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    /** Porta efetiva (útil quando se inicia na porta 0, como nos testes). */
    public int porta() {
        return servidor.getLocalPort();
    }

    public void parar() {
        try {
            servidor.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Falha ao fechar o socket", e);
        }
        threads.shutdownNow();
    }

    private void aceitar() {
        while (!servidor.isClosed()) {
            try {
                Socket conexao = servidor.accept();
                try {
                    threads.execute(() -> atender(conexao));
                } catch (RejectedExecutionException e) {
                    LOG.warning("Pool e fila cheios (ou servidor parando): conexão recusada");
                    fechar(conexao);
                }
            } catch (IOException e) {
                if (!servidor.isClosed()) {
                    LOG.log(Level.WARNING, "Falha ao aceitar conexão", e);
                }
            }
        }
    }

    private static void fechar(Socket conexao) {
        try {
            conexao.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Falha ao fechar a conexão recusada", e);
        }
    }

    private void atender(Socket conexao) {
        try (conexao;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter saida = new BufferedWriter(new OutputStreamWriter(conexao.getOutputStream(), StandardCharsets.UTF_8))) {
            conexao.setSoTimeout(TIMEOUT_LEITURA_MS);
            String linha;
            while ((linha = entrada.readLine()) != null) {
                saida.write(processar(linha).toString());
                saida.write("\n");
                saida.flush();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Conexão encerrada", e);
        }
    }

    /** Trata uma mensagem e devolve a resposta; nunca lança exceção. */
    ObjectNode processar(String linha) {
        JsonNode pedido;
        try {
            pedido = Json.MAPPER.readTree(linha);
        } catch (IOException e) {
            return erro("JSON_INVALIDO", "A mensagem não é um JSON válido.");
        }
        if (pedido == null || !pedido.isObject()) {
            return erro("JSON_INVALIDO", "A mensagem não é um JSON válido.");
        }
        // A chave vem antes de qualquer outra validação: quem não a tem não descobre nada.
        if (!chaveConfere(pedido.path("chaveInterna"))) {
            return erro("ACESSO_NEGADO", "Chave interna ausente ou errada.");
        }
        String nome = pedido.path("operacao").asText("");
        if (nome.isEmpty()) {
            return erro("OPERACAO_INVALIDA", "O campo operacao é obrigatório.");
        }
        Operacao operacao = operacoes.get(nome);
        if (operacao == null) {
            return erro("OPERACAO_DESCONHECIDA", "Operação desconhecida: " + nome);
        }
        try {
            JsonNode dados = operacao.executar(pedido.path("dados"));
            ObjectNode ok = Json.MAPPER.createObjectNode();
            ok.put("ok", true);
            ok.set("dados", dados == null ? Json.MAPPER.createObjectNode() : dados);
            return ok;
        } catch (ErroSocket e) {
            return erro(e.codigo(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha na operação " + nome, e);
            return erro("ERRO_INTERNO", "Algo deu errado. Tente de novo em instantes.");
        }
    }

    private boolean chaveConfere(JsonNode recebida) {
        if (!recebida.isTextual()) {
            return false;
        }
        // Comparação em tempo constante.
        return MessageDigest.isEqual(chaveInterna, recebida.asText().getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode erro(String codigo, String mensagem) {
        ObjectNode n = Json.MAPPER.createObjectNode();
        n.put("ok", false);
        n.put("erro", codigo);
        n.put("mensagem", mensagem);
        return n;
    }
}
