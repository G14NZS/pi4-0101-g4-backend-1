package br.pucampinas.reservice.controller;
import br.pucampinas.reservice.dto.horario.ListarHorariosLivresResponse;
import java.time.LocalDate;
import br.pucampinas.reservice.dto.horario.DefinirHorariosRequest;
import br.pucampinas.reservice.dto.horario.DefinirHorariosResponse;
import br.pucampinas.reservice.service.HorarioService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/horarios")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @PutMapping
    public ResponseEntity<DefinirHorariosResponse> definirHorarios(
            @RequestBody DefinirHorariosRequest request) {

        // TEMPORÁRIO:
        // depois deve vir do token do profissional autenticado.
        Long profissionalId = 3L;

        DefinirHorariosResponse response =
                horarioService.definirHorarios(
                        profissionalId,
                        request
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/livres")
    public ResponseEntity<ListarHorariosLivresResponse> listarHorariosLivres(
            @RequestParam Long profissionalId,
            @RequestParam String data,
            @RequestParam int duracaoMinutos) {

        LocalDate dataConvertida = LocalDate.parse(data);

        ListarHorariosLivresResponse response =
                horarioService.listarHorariosLivres(
                        profissionalId,
                        dataConvertida,
                        duracaoMinutos
                );

        return ResponseEntity.ok(response);
    }
}
