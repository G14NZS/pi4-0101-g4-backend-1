package br.pucampinas.reservice.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profissional_id", nullable = false)
    private Long profissionalId;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "status", nullable = false)
    private String status;

    public Agendamento() {
    }

    public Agendamento(
        Long profissionalId,
        LocalDate data,
        LocalTime horaInicio,
        LocalTime horaFim,
        String status) {

        this.profissionalId = profissionalId;
        this.data = data;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getProfissionalId() {
        return profissionalId;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    public String getStatus() {
        return status;
    }
}