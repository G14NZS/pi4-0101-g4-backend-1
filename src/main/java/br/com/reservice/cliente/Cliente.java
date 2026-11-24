package br.com.reservice.cliente;

import java.time.LocalDateTime;

/** Linha da tabela {@code cliente} (CT17). */
public record Cliente(int id, String nome, String telefone, LocalDateTime criadoEm) {
}
