package br.com.reservice.profissional;

import java.time.LocalDateTime;

/** Linha da tabela {@code profissional} (CT14). */
public record Profissional(
        int id,
        String nome,
        String email,
        String senhaHash,
        String telefone,
        String slug,
        LocalDateTime criadoEm) {
}
