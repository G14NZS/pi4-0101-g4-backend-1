package br.pucampinas.reservice.dto.horario;

import java.util.List;

public class DefinirHorariosRequest {

    private List<HorarioRequest> horarios;

    public DefinirHorariosRequest() {
    }

    public List<HorarioRequest> getHorarios() {
        return horarios;
    }

    public void setHorarios(List<HorarioRequest> horarios) {
        this.horarios = horarios;
    }
}
