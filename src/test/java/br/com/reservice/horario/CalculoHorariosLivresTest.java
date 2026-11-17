package br.com.reservice.horario;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regra do CT5 sem banco nem HTTP. Os quatro primeiros casos vêm do HorarioServiceTest da branch
 * feat/h03-grade-horarios (passo de 30 minutos, serviço cabe inteiro, encostar não é conflito).
 */
class CalculoHorariosLivresTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);          // segunda-feira
    private static final LocalDateTime ANTES_DE_TUDO = LocalDateTime.of(2026, 11, 1, 8, 0);

    private static HorarioAtendimento faixa(String inicio, String fim) {
        return new HorarioAtendimento(0, 1, "segunda", inicio, fim);
    }

    private static HorarioLivreRecurso.Periodo ocupado(int horaInicio, int horaFim) {
        return new HorarioLivreRecurso.Periodo(DIA.atTime(horaInicio, 0), DIA.atTime(horaFim, 0));
    }

    private static List<LocalTime> horas(List<LocalDateTime> inicios) {
        return inicios.stream().map(LocalDateTime::toLocalTime).toList();
    }

    @Test
    void ofereceUmInicioACada30MinutosEOServicoCabeInteiroNaFaixa() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "12:00")), List.of(), DIA, 60, ANTES_DE_TUDO);

        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0)), horas(livres));
    }

    @Test
    void naoOfereceHorarioQuandoOServicoNaoCabeNaFaixa() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "10:00")), List.of(), DIA, 90, ANTES_DE_TUDO);

        assertEquals(List.of(), livres);
    }

    @Test
    void removeOsHorariosQueSobrepoemAgendamentoConfirmado() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "12:00")), List.of(ocupado(10, 11)), DIA, 60, ANTES_DE_TUDO);

        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(11, 0)), horas(livres));
    }

    @Test
    void horarioQueApenasEncostaNoAgendamentoContinuaLivre() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "12:00")), List.of(ocupado(10, 11)), DIA, 60, ANTES_DE_TUDO);

        List<LocalTime> h = horas(livres);
        assertEquals(true, h.contains(LocalTime.of(9, 0)), "termina às 10:00, quando o agendamento começa");
        assertEquals(true, h.contains(LocalTime.of(11, 0)), "começa às 11:00, quando o agendamento termina");
        assertEquals(false, h.contains(LocalTime.of(9, 30)));
        assertEquals(false, h.contains(LocalTime.of(10, 0)));
        assertEquals(false, h.contains(LocalTime.of(10, 30)));
    }

    @Test
    void opassoContaDoInicioDeCadaFaixa() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:15", "10:45")), List.of(), DIA, 30, ANTES_DE_TUDO);

        assertEquals(List.of(LocalTime.of(9, 15), LocalTime.of(9, 45), LocalTime.of(10, 15)), horas(livres));
    }

    @Test
    void juntaAsFaixasDoDiaEmOrdem() {
        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "10:00"), faixa("14:00", "15:00")), List.of(), DIA, 60, ANTES_DE_TUDO);

        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(14, 0)), horas(livres));
    }

    @Test
    void semFaixaNoDiaNaoHaHorarios() {
        assertEquals(List.of(), HorarioLivreRecurso.calcular(List.of(), List.of(), DIA, 60, ANTES_DE_TUDO));
    }

    @Test
    void naoOfereceInicioQueJaPassouNemOAgoraExato() {
        LocalDateTime agora = DIA.atTime(10, 0);

        List<LocalDateTime> livres = HorarioLivreRecurso.calcular(
                List.of(faixa("09:00", "12:00")), List.of(), DIA, 60, agora);

        assertEquals(List.of(LocalTime.of(10, 30), LocalTime.of(11, 0)), horas(livres));
    }
}
