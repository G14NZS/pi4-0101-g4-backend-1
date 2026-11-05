package br.pucampinas.reservice.dto.horario;

import java.util.List;

public class ListarHorariosLivresResponse {

    private List<HorarioLivreResponse> horariosLivres;

    public ListarHorariosLivresResponse(List<HorarioLivreResponse> horariosLivres) {
        this.horariosLivres = horariosLivres;
    }

    public List<HorarioLivreResponse> getHorariosLivres() {
        return horariosLivres;
    }
}