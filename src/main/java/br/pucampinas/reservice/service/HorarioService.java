package br.pucampinas.reservice.service;

import br.pucampinas.reservice.dto.horario.DefinirHorariosRequest;
import br.pucampinas.reservice.dto.horario.DefinirHorariosResponse;
import br.pucampinas.reservice.dto.horario.HorarioRequest;
import br.pucampinas.reservice.model.HorarioAtendimento;
import br.pucampinas.reservice.repository.HorarioAtendimentoRepository;
import br.pucampinas.reservice.dto.horario.HorarioLivreResponse;
import br.pucampinas.reservice.dto.horario.ListarHorariosLivresResponse;
import br.pucampinas.reservice.model.Agendamento;
import br.pucampinas.reservice.repository.AgendamentoRepository;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final AgendamentoRepository agendamentoRepository;

    private static final Set<String> DIAS_VALIDOS = Set.of(
            "domingo",
            "segunda",
            "terca",
            "quarta",
            "quinta",
            "sexta",
            "sabado"
    );

    public HorarioService(
            HorarioAtendimentoRepository repository,
            AgendamentoRepository agendamentoRepository) {

        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
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

    public ListarHorariosLivresResponse listarHorariosLivres(
        Long profissionalId,
        LocalDate data,
        int duracaoMinutos) {

    String diaSemana = converterDiaSemana(data);

    List<HorarioAtendimento> faixas =
            repository.findByProfissionalId(profissionalId)
                    .stream()
                    .filter(h -> h.getDiaSemana().equals(diaSemana))
                    .toList();

    List<Agendamento> agendamentosConfirmados =
            agendamentoRepository.findByProfissionalIdAndDataAndStatus(
                    profissionalId,
                    data,
                    "CONFIRMADO"
            );

    List<HorarioLivreResponse> horariosLivres =
            new ArrayList<>();

    for (HorarioAtendimento faixa : faixas) {

        LocalTime inicioFaixa =
                LocalTime.parse(faixa.getHoraInicio());

        LocalTime fimFaixa =
                LocalTime.parse(faixa.getHoraFim());

        LocalTime inicioPossivel = inicioFaixa;

        while (!inicioPossivel
                .plusMinutes(duracaoMinutos)
                .isAfter(fimFaixa)) {

            LocalTime fimPossivel =
                    inicioPossivel.plusMinutes(duracaoMinutos);

            boolean conflito = false;

            for (Agendamento agendamento :
                    agendamentosConfirmados) {

                boolean sobrepoe =
                        inicioPossivel.isBefore(
                                agendamento.getHoraFim())
                        &&
                        agendamento.getHoraInicio()
                                .isBefore(fimPossivel);

                if (sobrepoe) {
                    conflito = true;
                    break;
                }
            }

            if (!conflito) {
                horariosLivres.add(
                        new HorarioLivreResponse(
                                inicioPossivel.toString(),
                                fimPossivel.toString()
                        )
                );
            }

            inicioPossivel =
                    inicioPossivel.plusMinutes(30);
        }
    }

    return new ListarHorariosLivresResponse(
            horariosLivres
    );
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
    private String converterDiaSemana(LocalDate data) {

        return switch (data.getDayOfWeek()) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terca";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sabado";
            case SUNDAY -> "domingo";
        };
    }
}
