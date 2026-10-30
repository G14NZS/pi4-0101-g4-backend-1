package br.com.reservice;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Configuração lida das variáveis de ambiente (ver docker-compose.yml). */
public record Config(
        int porta,
        int threads,
        String bancoUrl,
        String bancoUsuario,
        String bancoSenha,
        String jwtSegredo,
        Duration jwtValidade,
        Set<String> corsOrigens) {

    public static Config doAmbiente() {
        return de(System.getenv());
    }

    static Config de(Map<String, String> env) {
        String segredo = obrigatoria(env, "JWT_SEGREDO");
        if (segredo.length() < 32) {
            throw new IllegalStateException("JWT_SEGREDO precisa ter pelo menos 32 caracteres");
        }
        return new Config(
                Integer.parseInt(env.getOrDefault("PORTA", "8080")),
                Integer.parseInt(env.getOrDefault("THREADS", "16")),
                obrigatoria(env, "DB_URL"),
                obrigatoria(env, "DB_USUARIO"),
                env.getOrDefault("DB_SENHA", ""),
                segredo,
                Duration.ofMinutes(Long.parseLong(env.getOrDefault("JWT_VALIDADE_MINUTOS", "120"))),
                Arrays.stream(env.getOrDefault("CORS_ORIGENS", "http://localhost:5175").split(","))
                        .map(String::trim)
                        .filter(o -> !o.isEmpty())
                        .collect(Collectors.toUnmodifiableSet()));
    }

    private static String obrigatoria(Map<String, String> env, String nome) {
        String valor = env.get(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + nome + " não definida");
        }
        return valor;
    }
}
