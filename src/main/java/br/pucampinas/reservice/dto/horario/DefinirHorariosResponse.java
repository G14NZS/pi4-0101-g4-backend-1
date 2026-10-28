package br.pucampinas.reservice.dto.horario;

import java.time.LocalDateTime;
import java.util.List;

public class DefinirHorariosResponse {

    private List<HorarioRequest> horarios;
    private LocalDateTime atualizadoEm;

    public DefinirHorariosResponse(
            List<HorarioRequest> horarios,
            LocalDateTime atualizadoEm) {

        this.horarios = horarios;
        this.atualizadoEm = atualizadoEm;
    }

    public List<HorarioRequest> getHorarios() {
        return horarios;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
