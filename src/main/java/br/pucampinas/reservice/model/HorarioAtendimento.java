package br.pucampinas.reservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "horario_atendimento")
public class HorarioAtendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "dia_semana", nullable = false)
    private String diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private String horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private String horaFim;

    public HorarioAtendimento() {
    }

    public HorarioAtendimento(
            Long profissionalId,
            String diaSemana,
            String horaInicio,
            String horaFim) {

        this.profissionalId = profissionalId;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }

    public Long getId() {
        return id;
    }

    public Long getProfissionalId() {
        return profissionalId;
    }

    public void setProfissionalId(Long profissionalId) {
        this.profissionalId = profissionalId;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public String getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(String horaInicio) {
        this.horaInicio = horaInicio;
    }

    public String getHoraFim() {
        return horaFim;
    }

    public void setHoraFim(String horaFim) {
        this.horaFim = horaFim;
    }
}
