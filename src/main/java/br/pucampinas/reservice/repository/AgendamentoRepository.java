package br.pucampinas.reservice.repository;

import br.pucampinas.reservice.model.Agendamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AgendamentoRepository
        extends JpaRepository<Agendamento, Long> {

    List<Agendamento> findByProfissionalIdAndDataAndStatus(
            Long profissionalId,
            LocalDate data,
            String status
    );
}