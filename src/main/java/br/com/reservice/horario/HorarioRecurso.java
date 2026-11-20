package br.com.reservice.horario;

import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.banco.Banco;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** CT4 definirHorarios ({@code PUT /horarios}): substitui a grade semanal inteira do profissional do token. */
public class HorarioRecurso {

    static final Set<String> DIAS = Set.of("domingo", "segunda", "terca", "quarta", "quinta", "sexta", "sabado");
    static final int FAIXAS_MAXIMAS = 100;
    private static final Pattern HORA = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    public record Horario(String diaSemana, String horaInicio, String horaFim) {
    }

    public record GradeDefinida(List<Horario> horarios, LocalDateTime atualizadoEm) {
    }

    private final Banco banco;
    private final HorarioRepositorio horarios;
    private final AgendamentoRepositorio agendamentos;
    private final Tokens tokens;

    public HorarioRecurso(Banco banco, HorarioRepositorio horarios, AgendamentoRepositorio agendamentos, Tokens tokens) {
        this.banco = banco;
        this.horarios = horarios;
        this.agendamentos = agendamentos;
        this.tokens = tokens;
    }

    public Resposta definirHorarios(Requisicao requisicao) throws SQLException {
        int profissionalId = tokens.profissionalAutenticado(requisicao);
        List<Horario> grade = lerHorarios(requisicao.corpoJson().get("horarios"));
        validarSobreposicoes(grade);

        List<HorarioAtendimento> faixas = grade.stream()
                .map(h -> new HorarioAtendimento(0, profissionalId, h.diaSemana(), h.horaInicio(), h.horaFim()))
                .toList();

        try (Connection c = banco.conectar()) {
            c.setAutoCommit(false);
            try {
                // Mesma trava do CT7: grade e agendamentos do profissional mudam um de cada vez.
                agendamentos.travarAgendaDoProfissional(c, profissionalId);
                horarios.substituirGrade(c, profissionalId, faixas);
                c.commit();
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                if (e.getErrorCode() == Banco.ERRO_CHAVE_ESTRANGEIRA) {
                    // Token válido de um profissional que não existe mais.
                    throw ErroApi.naoAutenticado();
                }
                throw e;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
        return Resposta.ok(new GradeDefinida(grade, Banco.agora()));
    }

    private static List<Horario> lerHorarios(JsonNode valor) {
        if (valor == null || !valor.isArray() || valor.size() > FAIXAS_MAXIMAS) {
            throw ErroApi.dadosInvalidos("Envie a lista de horários de atendimento.");
        }
        List<Horario> lista = new ArrayList<>();
        for (JsonNode item : valor) {
            if (!item.isObject()) {
                throw ErroApi.dadosInvalidos("Cada horário precisa ter dia, início e fim.");
            }
            JsonNode dia = item.get("diaSemana");
            if (dia == null || !dia.isTextual() || !DIAS.contains(dia.asText())) {
                throw ErroApi.dadosInvalidos("Escolha um dia da semana válido.");
            }
            String inicio = lerHora(item.get("horaInicio"));
            String fim = lerHora(item.get("horaFim"));
            if (LocalTime.parse(fim).compareTo(LocalTime.parse(inicio)) <= 0) {
                throw horarioInvalido();
            }
            lista.add(new Horario(dia.asText(), inicio, fim));
        }
        return lista;
    }

    private static String lerHora(JsonNode valor) {
        if (valor == null || !valor.isTextual() || !HORA.matcher(valor.asText()).matches()) {
            throw horarioInvalido();
        }
        return valor.asText();
    }

    private static ErroApi horarioInvalido() {
        return new ErroApi(400, "HORARIO_INVALIDO",
                "Use horas no formato HH:MM, com o fim depois do início.");
    }

    /** Faixas coladas (uma termina quando a outra começa) são permitidas; sobrepostas, não. */
    private static void validarSobreposicoes(List<Horario> grade) {
        for (int i = 0; i < grade.size(); i++) {
            for (int j = i + 1; j < grade.size(); j++) {
                Horario a = grade.get(i);
                Horario b = grade.get(j);
                if (a.diaSemana().equals(b.diaSemana())
                        && LocalTime.parse(a.horaInicio()).isBefore(LocalTime.parse(b.horaFim()))
                        && LocalTime.parse(b.horaInicio()).isBefore(LocalTime.parse(a.horaFim()))) {
                    throw new ErroApi(400, "HORARIOS_SOBREPOSTOS",
                            "Há horários no mesmo dia que se sobrepõem.");
                }
            }
        }
    }
}
