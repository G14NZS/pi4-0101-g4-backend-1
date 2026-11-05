package br.com.reservice.horario;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Linha da tabela {@code horario_atendimento} (CT16): uma faixa da grade semanal do profissional. */
public record HorarioAtendimento(int id, int profissionalId, String diaSemana, String horaInicio, String horaFim) {

    /** Valor de {@code dia_semana} correspondente ao dia da semana. */
    public static String diaSemana(DayOfWeek dia) {
        return switch (dia) {
            case SUNDAY -> "domingo";
            case MONDAY -> "segunda";
            case TUESDAY -> "terca";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sabado";
        };
    }

    /** Diz se o atendimento de {@code inicio} a {@code fim}, no mesmo dia, cabe inteiro nesta faixa. */
    public boolean comporta(LocalTime inicio, LocalTime fim) {
        return !inicio.isBefore(LocalTime.parse(horaInicio)) && !fim.isAfter(LocalTime.parse(horaFim));
    }
}
