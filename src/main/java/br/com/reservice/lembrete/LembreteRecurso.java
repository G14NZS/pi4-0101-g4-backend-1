package br.com.reservice.lembrete;

import br.com.reservice.agendamento.Agendamento;
import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.agendamento.AgendamentoRepositorio.LembretePendente;
import br.com.reservice.banco.Banco;
import br.com.reservice.http.Json;
import br.com.reservice.socket.ServidorSocket.ErroSocket;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Operações do socket TCP chamadas pelo Backend 2 (E6). A chave interna já foi conferida pelo
 * {@link br.com.reservice.socket.ServidorSocket} antes de chegar aqui.
 * <ul>
 *   <li>CT12 listarLembretesPendentes: confirmados, com lembrete pendente e início em {@code (agora, ate]}.</li>
 *   <li>CT13 registrarLembrete: grava enviado ou falhou uma vez só por agendamento.</li>
 * </ul>
 */
public class LembreteRecurso {

    public static final String LISTAR = "listarLembretesPendentes";
    public static final String REGISTRAR = "registrarLembrete";

    private static final Set<String> RESULTADOS = Set.of(Agendamento.LEMBRETE_ENVIADO, Agendamento.LEMBRETE_FALHOU);

    public record LembretesPendentes(List<LembretePendente> lembretes) {
    }

    public record LembreteRegistrado(int agendamentoId, String resultado, LocalDateTime registradoEm) {
    }

    private final Banco banco;
    private final AgendamentoRepositorio agendamentos;
    private final Clock relogio;

    public LembreteRecurso(Banco banco, AgendamentoRepositorio agendamentos, Clock relogio) {
        this.banco = banco;
        this.agendamentos = agendamentos;
        this.relogio = relogio;
    }

    /** CT12. {@code ate} no passado (ou igual a agora) dá lista vazia, não erro. */
    public JsonNode listarLembretesPendentes(JsonNode dados) throws SQLException {
        LocalDateTime ate = lerAte(dados.path("ate"));
        try (Connection c = banco.conectar()) {
            return Json.MAPPER.valueToTree(new LembretesPendentes(
                    agendamentos.listarLembretesPendentes(c, Banco.agora(relogio), ate)));
        }
    }

    /**
     * CT13. Se o SMS saiu mas este registro não chegar (ou falhar), o lembrete continua pendente e volta no
     * próximo CT12: o cliente pode receber o lembrete de novo, mas não fica sem ele.
     */
    public JsonNode registrarLembrete(JsonNode dados) throws SQLException {
        int agendamentoId = lerAgendamentoId(dados.path("agendamentoId"));
        String resultado = lerResultado(dados.path("resultado"));
        LocalDateTime registradoEm = Banco.agora(relogio);
        try (Connection c = banco.conectar()) {
            if (!agendamentos.registrarLembrete(c, agendamentoId, resultado, registradoEm)) {
                if (!agendamentos.existe(c, agendamentoId)) {
                    throw new ErroSocket("AGENDAMENTO_NAO_ENCONTRADO", "Agendamento " + agendamentoId + " não existe.");
                }
                throw new ErroSocket("LEMBRETE_JA_REGISTRADO",
                        "O lembrete do agendamento " + agendamentoId + " já foi registrado.");
            }
        }
        return Json.MAPPER.valueToTree(new LembreteRegistrado(agendamentoId, resultado, registradoEm));
    }

    /** Data e hora sem fuso, no horário de Brasília, como "2026-10-20T12:15:00". */
    private static LocalDateTime lerAte(JsonNode valor) {
        if (!valor.isTextual()) {
            throw dataInvalida();
        }
        try {
            return LocalDateTime.parse(valor.asText());
        } catch (DateTimeParseException e) {
            throw dataInvalida();
        }
    }

    private static ErroSocket dataInvalida() {
        return new ErroSocket("DATA_INVALIDA", "Informe ate como data e hora, por exemplo 2026-10-20T12:15:00.");
    }

    private static int lerAgendamentoId(JsonNode valor) {
        if (!valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw new ErroSocket("DADOS_INVALIDOS", "Informe agendamentoId como número inteiro.");
        }
        return valor.asInt();
    }

    private static String lerResultado(JsonNode valor) {
        if (!valor.isTextual() || !RESULTADOS.contains(valor.asText())) {
            throw new ErroSocket("DADOS_INVALIDOS", "Informe resultado como enviado ou falhou.");
        }
        return valor.asText();
    }
}
