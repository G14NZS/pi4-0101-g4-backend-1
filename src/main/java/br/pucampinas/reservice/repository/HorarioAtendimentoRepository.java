package br.pucampinas.reservice.repository;

import br.pucampinas.reservice.model.HorarioAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioAtendimentoRepository
        extends JpaRepository<HorarioAtendimento, Long> {

    List<HorarioAtendimento> findByProfissionalId(Long profissionalId);

    void deleteByProfissionalId(Long profissionalId);
}
