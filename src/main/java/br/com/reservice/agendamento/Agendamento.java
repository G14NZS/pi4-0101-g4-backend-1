package br.com.reservice.agendamento;

import java.time.LocalDateTime;

/** Linha da tabela {@code agendamento} (CT18). */
public record Agendamento(
        int id,
        int profissionalId,
        int servicoId,
        int clienteId,
        LocalDateTime inicio,
        LocalDateTime fim,
        String status,
        String lembreteStatus,
        LocalDateTime lembreteRegistradoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static final String CONFIRMADO = "confirmado";
    public static final String LEMBRETE_PENDENTE = "pendente";
}
