package br.com.reservice.cliente;

import java.time.LocalDateTime;

public record RecuperacaoSenha(
        int id,
        int clienteId,
        String codigoHash,
        LocalDateTime expiraEm,
        LocalDateTime utilizadoEm,
        LocalDateTime criadoEm) {
}