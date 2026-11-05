package br.pucampinas.reservice.service;
import br.pucampinas.reservice.model.Agendamento;
import br.pucampinas.reservice.model.HorarioAtendimento;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.when;
import br.pucampinas.reservice.dto.horario.DefinirHorariosRequest;
import br.pucampinas.reservice.dto.horario.HorarioRequest;
import br.pucampinas.reservice.repository.HorarioAtendimentoRepository;
import br.pucampinas.reservice.repository.AgendamentoRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock
    private HorarioAtendimentoRepository repository;

    @InjectMocks
    private HorarioService service;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Test
    void deveDefinirHorariosValidos() {

        HorarioRequest horario = new HorarioRequest();
        horario.setDiaSemana("segunda");
        horario.setHoraInicio("09:00");
        horario.setHoraFim("12:00");

        DefinirHorariosRequest request =
                new DefinirHorariosRequest();

        request.setHorarios(List.of(horario));

        assertDoesNotThrow(() ->
                service.definirHorarios(1L, request)
        );

        verify(repository)
                .deleteByProfissionalId(1L);

        verify(repository, times(1))
                .save(any());
    }

    @Test
    void deveRejeitarHorarioComFimAntesDoInicio() {

        HorarioRequest horario = new HorarioRequest();
        horario.setDiaSemana("segunda");
        horario.setHoraInicio("12:00");
        horario.setHoraFim("09:00");

        DefinirHorariosRequest request =
                new DefinirHorariosRequest();

        request.setHorarios(List.of(horario));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.definirHorarios(1L, request)
                );

        assertEquals(
                "HORARIO_INVALIDO",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void deveRejeitarHorariosSobrepostos() {

        HorarioRequest primeiro =
                new HorarioRequest();

        primeiro.setDiaSemana("segunda");
        primeiro.setHoraInicio("09:00");
        primeiro.setHoraFim("12:00");

        HorarioRequest segundo =
                new HorarioRequest();

        segundo.setDiaSemana("segunda");
        segundo.setHoraInicio("11:00");
        segundo.setHoraFim("14:00");

        DefinirHorariosRequest request =
                new DefinirHorariosRequest();

        request.setHorarios(
                List.of(primeiro, segundo)
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.definirHorarios(1L, request)
                );

        assertEquals(
                "HORARIOS_SOBREPOSTOS",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void deveAceitarHorariosEncostadosSemSobreposicao() {

        HorarioRequest primeiro =
                new HorarioRequest();

        primeiro.setDiaSemana("segunda");
        primeiro.setHoraInicio("09:00");
        primeiro.setHoraFim("12:00");

        HorarioRequest segundo =
                new HorarioRequest();

        segundo.setDiaSemana("segunda");
        segundo.setHoraInicio("12:00");
        segundo.setHoraFim("15:00");

        DefinirHorariosRequest request =
                new DefinirHorariosRequest();

        request.setHorarios(
                List.of(primeiro, segundo)
        );

        assertDoesNotThrow(() ->
                service.definirHorarios(1L, request)
        );

        verify(repository)
                .deleteByProfissionalId(1L);

        verify(repository, times(2))
                .save(any());
    }
    @Test
    void deveListarHorariosLivresDe30Em30Minutos() {

        HorarioAtendimento faixa =
                new HorarioAtendimento(
                        1L,
                        "segunda",
                        "09:00",
                        "12:00"
                );

        when(repository.findByProfissionalId(1L))
                .thenReturn(List.of(faixa));

        when(agendamentoRepository
                .findByProfissionalIdAndDataAndStatus(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        "CONFIRMADO"
                ))
                .thenReturn(List.of());

        var response =
                service.listarHorariosLivres(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        60
                );

        assertEquals(5, response.getHorariosLivres().size());

        assertEquals(
                "09:00",
                response.getHorariosLivres()
                        .get(0)
                        .getHoraInicio()
        );

        assertEquals(
                "11:00",
                response.getHorariosLivres()
                        .get(4)
                        .getHoraInicio()
        );
    }
    @Test
    void naoDeveGerarHorarioQuandoServicoNaoCabeNaFaixa() {

        HorarioAtendimento faixa =
                new HorarioAtendimento(
                        1L,
                        "segunda",
                        "09:00",
                        "10:00"
                );

        when(repository.findByProfissionalId(1L))
                .thenReturn(List.of(faixa));

        when(agendamentoRepository
                .findByProfissionalIdAndDataAndStatus(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        "CONFIRMADO"
                ))
                .thenReturn(List.of());

        var response =
                service.listarHorariosLivres(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        90
                );

        assertEquals(
                0,
                response.getHorariosLivres().size()
        );
    }
    @Test
    void deveRemoverHorariosQueConflitamComAgendamentoConfirmado() {

        HorarioAtendimento faixa =
                new HorarioAtendimento(
                        1L,
                        "segunda",
                        "09:00",
                        "12:00"
                );

        Agendamento agendamento =
                new Agendamento(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        "CONFIRMADO"
                );

        when(repository.findByProfissionalId(1L))
                .thenReturn(List.of(faixa));

        when(agendamentoRepository
                .findByProfissionalIdAndDataAndStatus(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        "CONFIRMADO"
                ))
                .thenReturn(List.of(agendamento));

        var response =
                service.listarHorariosLivres(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        60
                );

        assertEquals(
                2,
                response.getHorariosLivres().size()
        );

        assertEquals(
                "09:00",
                response.getHorariosLivres()
                        .get(0)
                        .getHoraInicio()
        );

        assertEquals(
                "11:00",
                response.getHorariosLivres()
                        .get(1)
                        .getHoraInicio()
        );
    }
    @Test
    void devePermitirHorarioQueEncostaNoAgendamento() {

        HorarioAtendimento faixa =
                new HorarioAtendimento(
                        1L,
                        "segunda",
                        "09:00",
                        "12:00"
                );

        Agendamento agendamento =
                new Agendamento(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        "CONFIRMADO"
                );

        when(repository.findByProfissionalId(1L))
                .thenReturn(List.of(faixa));

        when(agendamentoRepository
                .findByProfissionalIdAndDataAndStatus(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        "CONFIRMADO"
                ))
                .thenReturn(List.of(agendamento));

        var response =
                service.listarHorariosLivres(
                        1L,
                        LocalDate.of(2026, 11, 9),
                        60
                );

        assertTrue(
                response.getHorariosLivres()
                        .stream()
                        .anyMatch(h ->
                                h.getHoraInicio()
                                        .equals("09:00")
                        )
        );

        assertTrue(
                response.getHorariosLivres()
                        .stream()
                        .anyMatch(h ->
                                h.getHoraInicio()
                                        .equals("11:00")
                        )
        );
    }
}