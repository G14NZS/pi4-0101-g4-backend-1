package br.pucampinas.reservice.service;

import br.pucampinas.reservice.dto.horario.DefinirHorariosRequest;
import br.pucampinas.reservice.dto.horario.DefinirHorariosResponse;
import br.pucampinas.reservice.dto.horario.HorarioRequest;
import br.pucampinas.reservice.model.HorarioAtendimento;
import br.pucampinas.reservice.repository.HorarioAtendimentoRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Service
public class HorarioService {

    private final HorarioAtendimentoRepository repository;

    private static final Set<String> DIAS_VALIDOS = Set.of(
            "domingo",
            "segunda",
            "terca",
            "quarta",
            "quinta",
            "sexta",
            "sabado"
    );

    public HorarioService(HorarioAtendimentoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DefinirHorariosResponse definirHorarios(
            Long profissionalId,
            DefinirHorariosRequest request) {

        List<HorarioRequest> horarios = request.getHorarios();

        if (horarios == null) {
            throw new IllegalArgumentException("DADOS_INVALIDOS");
        }

        validarHorarios(horarios);

        repository.deleteByProfissionalId(profissionalId);

        for (HorarioRequest horario : horarios) {

            HorarioAtendimento entidade =
                    new HorarioAtendimento(
                            profissionalId,
                            horario.getDiaSemana(),
                            horario.getHoraInicio(),
                            horario.getHoraFim()
                    );

            repository.save(entidade);
        }

        return new DefinirHorariosResponse(
                horarios,
                LocalDateTime.now()
        );
    }

    private void validarHorarios(List<HorarioRequest> horarios) {

        for (HorarioRequest horario : horarios) {

            if (!DIAS_VALIDOS.contains(horario.getDiaSemana())) {
                throw new IllegalArgumentException("DADOS_INVALIDOS");
            }

            LocalTime inicio;
            LocalTime fim;

            try {
                inicio = LocalTime.parse(horario.getHoraInicio());
                fim = LocalTime.parse(horario.getHoraFim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("HORARIO_INVALIDO");
            }

            if (!fim.isAfter(inicio)) {
                throw new IllegalArgumentException("HORARIO_INVALIDO");
            }
        }

        validarSobreposicoes(horarios);
    }

    private void validarSobreposicoes(List<HorarioRequest> horarios) {

        for (int i = 0; i < horarios.size(); i++) {

            HorarioRequest a = horarios.get(i);

            for (int j = i + 1; j < horarios.size(); j++) {

                HorarioRequest b = horarios.get(j);

                if (!a.getDiaSemana().equals(b.getDiaSemana())) {
                    continue;
                }

                LocalTime inicioA = LocalTime.parse(a.getHoraInicio());
                LocalTime fimA = LocalTime.parse(a.getHoraFim());

                LocalTime inicioB = LocalTime.parse(b.getHoraInicio());
                LocalTime fimB = LocalTime.parse(b.getHoraFim());

                boolean sobrepoe =
                        inicioA.isBefore(fimB)
                        && inicioB.isBefore(fimA);

                if (sobrepoe) {
                    throw new IllegalArgumentException(
                            "HORARIOS_SOBREPOSTOS"
                    );
                }
            }
        }
    }
}
