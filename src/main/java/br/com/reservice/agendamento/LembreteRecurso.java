package br.com.reservice.agendamento;

import br.com.reservice.agendamento.AgendamentoRepositorio.LembretePendente;
import br.com.reservice.banco.Banco;
import br.com.reservice.http.Json;
import br.com.reservice.socket.ServidorSocket.ErroSocket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Operações do socket TCP usadas pelo Backend 2 no lembrete automático (E6):
 * CT12 {@code listarLembretesPendentes} e CT13 {@code registrarLembrete}.
 * A chave interna é conferida pelo {@code ServidorSocket} antes de chegar aqui.
 */
public class LembreteRecurso {

    static final String ENVIADO = "enviado";
    static final String FALHOU = "falhou";
    private static final Set<String> RESULTADOS = Set.of(ENVIADO, FALHOU);

    private final Banco banco;
    private final AgendamentoRepositorio agendamentos;

    public LembreteRecurso(Banco banco, AgendamentoRepositorio agendamentos) {
        this.banco = banco;
        this.agendamentos = agendamentos;
    }

    /**
     * CT12: agendamentos confirmados que começam entre agora e {@code ate} e ainda não tiveram o
     * lembrete registrado. As datas são sem fuso, no horário de Brasília (como no resto do sistema).
     */
    public JsonNode listarLembretesPendentes(JsonNode dados) throws SQLException {
        LocalDateTime ate = lerData(dados.path("ate"));
        try (Connection c = banco.conectar()) {
            List<LembretePendente> lembretes = agendamentos.listarLembretesPendentes(c, Banco.agora(), ate);
            ObjectNode saida = Json.MAPPER.createObjectNode();
            saida.set("lembretes", Json.MAPPER.valueToTree(lembretes));
            return saida;
        }
    }

    /** CT13: grava se o SMS foi enviado ou falhou. Cada agendamento só aceita um registro. */
    public JsonNode registrarLembrete(JsonNode dados) throws SQLException {
        JsonNode id = dados.path("agendamentoId");
        JsonNode resultado = dados.path("resultado");
        if (!id.isIntegralNumber() || !id.canConvertToInt() || id.asInt() <= 0
                || !resultado.isTextual() || !RESULTADOS.contains(resultado.asText())) {
            throw new ErroSocket("DADOS_INVALIDOS",
                    "Informe agendamentoId (inteiro positivo) e resultado (enviado ou falhou).");
        }
        int agendamentoId = id.asInt();
        LocalDateTime agora = Banco.agora();
        try (Connection c = banco.conectar()) {
            if (!agendamentos.registrarLembrete(c, agendamentoId, resultado.asText(), agora)) {
                if (!agendamentos.existe(c, agendamentoId)) {
                    throw new ErroSocket("AGENDAMENTO_NAO_ENCONTRADO", "O agendamento não existe.");
                }
                throw new ErroSocket("LEMBRETE_JA_REGISTRADO", "O lembrete desse agendamento já foi registrado.");
            }
        }
        ObjectNode saida = Json.MAPPER.createObjectNode();
        saida.put("agendamentoId", agendamentoId);
        saida.put("resultado", resultado.asText());
        saida.put("registradoEm", agora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); // sempre com segundos
        return saida;
    }

    private static LocalDateTime lerData(JsonNode valor) {
        if (!valor.isTextual()) {
            throw new ErroSocket("DATA_INVALIDA", "O campo ate é obrigatório (AAAA-MM-DDTHH:MM:SS).");
        }
        try {
            return LocalDateTime.parse(valor.asText());
        } catch (DateTimeParseException e) {
            throw new ErroSocket("DATA_INVALIDA", "O campo ate está fora do formato AAAA-MM-DDTHH:MM:SS.");
        }
    }
}
