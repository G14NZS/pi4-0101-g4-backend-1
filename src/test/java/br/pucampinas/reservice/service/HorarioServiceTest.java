package br.pucampinas.reservice.service;

import br.pucampinas.reservice.dto.horario.DefinirHorariosRequest;
import br.pucampinas.reservice.dto.horario.HorarioRequest;
import br.pucampinas.reservice.repository.HorarioAtendimentoRepository;

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
}