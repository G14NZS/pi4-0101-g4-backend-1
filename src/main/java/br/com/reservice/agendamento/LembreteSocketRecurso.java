package br.com.reservice.agendamento;

import br.com.reservice.banco.Banco;
import br.com.reservice.http.Json;
import br.com.reservice.socket.ServidorSocket.ErroSocket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Operações de socket do E6 chamadas pelo Backend 2: CT12 (listar lembretes) e CT13 (registrar envio). */
public class LembreteSocketRecurso {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Banco banco;
    private final AgendamentoRepositorio agendamentos;

    public LembreteSocketRecurso(Banco banco, AgendamentoRepositorio agendamentos) {
        this.banco = banco;
        this.agendamentos = agendamentos;
    }

    /** CT12: lembretes pendentes de agendamentos que começam de agora até {@code ate}. */
    public JsonNode listarLembretesPendentes(JsonNode dados) throws SQLException {
        LocalDateTime ate = lerDataHora(dados.path("ate"));

        ArrayNode lista = Json.MAPPER.createArrayNode();
        try (Connection c = banco.conectar()) {
            for (AgendamentoRepositorio.LembretePendente l
                    : agendamentos.listarLembretesPendentes(c, Banco.agora(), ate)) {
                lista.addObject()
                        .put("agendamentoId", l.agendamentoId())
                        .put("clienteNome", l.clienteNome())
                        .put("clienteTelefone", l.clienteTelefone())
                        .put("servicoNome", l.servicoNome())
                        .put("profissionalNome", l.profissionalNome())
                        .put("inicio", DATA_HORA.format(l.inicio()));
            }
        }

        ObjectNode resposta = Json.MAPPER.createObjectNode();
        resposta.set("lembretes", lista);
        return resposta;
    }

    /** CT13: grava se o SMS do lembrete foi enviado ou falhou. Cada lembrete só é registrado uma vez. */
    public JsonNode registrarLembrete(JsonNode dados) throws SQLException {
        JsonNode id = dados.path("agendamentoId");
        String resultado = dados.path("resultado").asText("");

        if (!id.canConvertToInt() || !id.isIntegralNumber()
                || !(Agendamento.LEMBRETE_ENVIADO.equals(resultado)
                        || Agendamento.LEMBRETE_FALHOU.equals(resultado))) {
            throw new ErroSocket("DADOS_INVALIDOS",
                    "Informe agendamentoId e resultado (enviado ou falhou).");
        }
        int agendamentoId = id.asInt();

        LocalDateTime registradoEm = Banco.agora().truncatedTo(ChronoUnit.SECONDS);

        try (Connection c = banco.conectar()) {
            c.setAutoCommit(false);
            try {
                Optional<String> situacao = agendamentos.travarLembrete(c, agendamentoId);
                if (situacao.isEmpty()) {
                    throw new ErroSocket("AGENDAMENTO_NAO_ENCONTRADO", "Agendamento não encontrado.");
                }
                if (!Agendamento.LEMBRETE_PENDENTE.equals(situacao.get())) {
                    throw new ErroSocket("LEMBRETE_JA_REGISTRADO",
                            "O lembrete desse agendamento já foi registrado.");
                }
                agendamentos.registrarLembrete(c, agendamentoId, resultado, registradoEm);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }

        return Json.MAPPER.createObjectNode()
                .put("agendamentoId", agendamentoId)
                .put("resultado", resultado)
                .put("registradoEm", DATA_HORA.format(registradoEm));
    }

    private static LocalDateTime lerDataHora(JsonNode valor) {
        if (valor.isTextual()) {
            try {
                return LocalDateTime.parse(valor.asText());
            } catch (DateTimeParseException e) {
                // cai no erro abaixo
            }
        }
        throw new ErroSocket("DATA_INVALIDA", "Informe ate no formato AAAA-MM-DDTHH:MM:SS.");
    }
}
