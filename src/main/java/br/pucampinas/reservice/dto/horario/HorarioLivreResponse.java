package br.pucampinas.reservice.dto.horario;

public class HorarioLivreResponse {

    private String horaInicio;
    private String horaFim;

    public HorarioLivreResponse() {
    }

    public HorarioLivreResponse(String horaInicio, String horaFim) {
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }

    public String getHoraInicio() {
        return horaInicio;
    }

    public String getHoraFim() {
        return horaFim;
    }
}