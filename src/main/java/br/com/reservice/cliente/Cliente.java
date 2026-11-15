package br.com.reservice.cliente;

import java.time.LocalDateTime;

/** Linha da tabela {@code cliente} (CT17). */
public record Cliente(
        int id,
        String nome,
        String telefone,
        String senhaHash,
        LocalDateTime criadoEm) {
}