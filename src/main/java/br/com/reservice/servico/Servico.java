package br.com.reservice.servico;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Linha da tabela {@code servico} (CT15). */
public record Servico(
        int id,
        int profissionalId,
        String nome,
        int duracaoMinutos,
        BigDecimal preco,
        LocalDateTime criadoEm) {
}
