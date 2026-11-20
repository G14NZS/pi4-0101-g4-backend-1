package br.com.reservice.agendamento;

import br.com.reservice.agendamento.AgendamentoRepositorio.AgendamentoDoDia;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.Cliente;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.horario.HorarioRepositorio;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.servico.Servico;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Operações de agendamento.
 *
 * CT7: criar agendamento com cliente autenticado.
 * CT8: listar agendamentos futuros do cliente autenticado.
 * CT9: cancelar agendamento do cliente autenticado.
 * CT11: listar agenda do dia do profissional autenticado.
 */
public class AgendamentoRecurso {

    public record AgendamentoCriado(
            int agendamentoId,
            int servicoId,
            String servicoNome,
            LocalDateTime inicio,
            LocalDateTime fim,
            int clienteId,
            String clienteNome,
            String status) {
    }

    public record AgendamentosDoDia(
            LocalDate data,
            List<AgendamentoDoDia> agendamentos) {
    }

    public record AgendamentosDoCliente(
            List<AgendamentoRepositorio.ItemAgendamentoCliente> agendamentos) {
    }

    public record AgendamentoCancelado(
            int agendamentoId,
            String status,
            LocalDateTime atualizadoEm) {
    }

    private record Pedido(
            int servicoId,
            LocalDateTime inicio) {
    }

    private final Banco banco;
    private final ServicoRepositorio servicos;
    private final HorarioRepositorio horarios;
    private final ClienteRepositorio clientes;
    private final AgendamentoRepositorio agendamentos;
    private final Tokens tokens;

    public AgendamentoRecurso(
            Banco banco,
            ServicoRepositorio servicos,
            HorarioRepositorio horarios,
            ClienteRepositorio clientes,
            AgendamentoRepositorio agendamentos,
            Tokens tokens) {

        this.banco = banco;
        this.servicos = servicos;
        this.horarios = horarios;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.tokens = tokens;
    }

    /**
     * CT11 GET /agendamentos?data=AAAA-MM-DD
     * Agenda do profissional autenticado.
     */
    public Resposta listarAgendamentosDoDia(
            Requisicao requisicao) throws SQLException {

        int profissionalId =
                tokens.profissionalAutenticado(requisicao);

        LocalDate data =
                lerData(
                        requisicao
                                .parametroConsulta("data")
                                .orElse(null));

        try (Connection c = banco.conectar()) {

            return Resposta.ok(
                    new AgendamentosDoDia(
                            data,
                            agendamentos.listarConfirmadosDoDia(
                                    c,
                                    profissionalId,
                                    data)));
        }
    }

    /**
     * CT7 POST /agendamentos
     */
    public Resposta criarAgendamento(
            Requisicao requisicao) throws SQLException {

        int clienteId =
                tokens.clienteAutenticado(requisicao);

        JsonNode corpo =
                requisicao.corpoJson();

        Pedido pedido =
                new Pedido(
                        lerServicoId(
                                corpo.get("servicoId")),
                        lerInicio(
                                corpo.get("inicio")));

        try (Connection c = banco.conectar()) {

            c.setTransactionIsolation(
                    Connection.TRANSACTION_READ_COMMITTED);

            c.setAutoCommit(false);

            try {
                AgendamentoCriado criado =
                        gravar(
                                c,
                                pedido,
                                clienteId);

                c.commit();

                return Resposta.criado(criado);

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * CT8 GET /agendamentos/do-cliente
     * Lista somente os agendamentos do cliente autenticado.
     */
    public Resposta listarDoCliente(
            Requisicao requisicao) throws SQLException {

        int clienteId =
                tokens.clienteAutenticado(requisicao);

        try (Connection c = banco.conectar()) {

            List<AgendamentoRepositorio.ItemAgendamentoCliente> itens =
                    agendamentos.listarFuturosDoCliente(
                            c,
                            clienteId);

            return Resposta.ok(
                    new AgendamentosDoCliente(itens));
        }
    }

    /**
     * CT9 PATCH /agendamentos/{id}/cancelamento
     * Só permite cancelar agendamento pertencente ao cliente autenticado.
     */
    public Resposta cancelarAgendamento(
            Requisicao requisicao) throws SQLException {

        int clienteId =
                tokens.clienteAutenticado(requisicao);

        int agendamentoId =
                lerAgendamentoId(
                        requisicao.parametro(0));

        try (Connection c = banco.conectar()) {

            c.setAutoCommit(false);

            try {
                Agendamento agendamento =
                        agendamentos
                                .buscarPorIdEClienteParaAtualizar(
                                        c,
                                        agendamentoId,
                                        clienteId)
                                .orElseThrow(() ->
                                        new ErroApi(
                                                404,
                                                "AGENDAMENTO_NAO_ENCONTRADO",
                                                "Não encontramos esse agendamento."));

                if (!Agendamento.CONFIRMADO.equals(
                        agendamento.status())) {

                    throw new ErroApi(
                            409,
                            "AGENDAMENTO_JA_CANCELADO",
                            "Esse agendamento já foi cancelado.");
                }

                if (!agendamento.inicio()
                        .isAfter(Banco.agora())) {

                    throw new ErroApi(
                            409,
                            "AGENDAMENTO_JA_PASSOU",
                            "Esse agendamento já aconteceu ou o horário já passou.");
                }

                LocalDateTime atualizadoEm =
                        agendamentos.cancelar(
                                c,
                                agendamentoId);

                c.commit();

                return Resposta.ok(
                        new AgendamentoCancelado(
                                agendamentoId,
                                Agendamento.CANCELADO,
                                atualizadoEm));

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private AgendamentoCriado gravar(
            Connection c,
            Pedido pedido,
            int clienteId) throws SQLException {

        Servico servico =
                servicos.buscarPorId(
                                c,
                                pedido.servicoId())
                        .orElseThrow(() ->
                                new ErroApi(
                                        404,
                                        "SERVICO_NAO_ENCONTRADO",
                                        "Não encontramos esse serviço."));

        Cliente cliente =
                clientes.buscarPorId(
                                c,
                                clienteId)
                        .orElseThrow(
                                ErroApi::naoAutenticado);

        agendamentos.travarAgendaDoProfissional(
                c,
                servico.profissionalId());

        LocalDateTime inicio =
                pedido.inicio();

        LocalDateTime fim =
                inicio.plusMinutes(
                        servico.duracaoMinutos());

        if (!cabeNaGrade(
                c,
                servico.profissionalId(),
                inicio,
                fim)
                || agendamentos.existeConflito(
                c,
                servico.profissionalId(),
                inicio,
                fim)) {

            throw new ErroApi(
                    409,
                    "HORARIO_INDISPONIVEL",
                    "Esse horário não está mais disponível. Escolha outro.");
        }

        Agendamento a =
                agendamentos.inserir(
                        c,
                        servico.profissionalId(),
                        servico.id(),
                        cliente.id(),
                        inicio,
                        fim);

        return new AgendamentoCriado(
                a.id(),
                servico.id(),
                servico.nome(),
                a.inicio(),
                a.fim(),
                cliente.id(),
                cliente.nome(),
                a.status());
    }

    private boolean cabeNaGrade(
            Connection c,
            int profissionalId,
            LocalDateTime inicio,
            LocalDateTime fim) throws SQLException {

        if (!fim.toLocalDate()
                .equals(inicio.toLocalDate())) {

            return false;
        }

        return horarios
                .listarDoDia(
                        c,
                        profissionalId,
                        inicio.getDayOfWeek())
                .stream()
                .anyMatch(faixa ->
                        faixa.comporta(
                                inicio.toLocalTime(),
                                fim.toLocalTime()));
    }

    private static int lerAgendamentoId(
            String valor) {

        try {
            int id =
                    Integer.parseInt(valor);

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException e) {

            throw new ErroApi(
                    404,
                    "AGENDAMENTO_NAO_ENCONTRADO",
                    "Não encontramos esse agendamento.");
        }
    }

    private static LocalDate lerData(
            String valor) {

        if (valor == null) {
            throw dataInvalida();
        }

        try {
            return LocalDate.parse(
                    valor.trim());

        } catch (DateTimeParseException e) {
            throw dataInvalida();
        }
    }

    private static ErroApi dataInvalida() {

        return new ErroApi(
                400,
                "DATA_INVALIDA",
                "Informe a data no formato AAAA-MM-DD, por exemplo 2026-10-20.");
    }

    private static int lerServicoId(
            JsonNode valor) {

        if (valor == null
                || !valor.isIntegralNumber()
                || !valor.canConvertToInt()) {

            throw ErroApi.dadosInvalidos(
                    "Escolha o serviço.");
        }

        return valor.asInt();
    }

    private static LocalDateTime lerInicio(
            JsonNode valor) {

        ErroApi invalida =
                new ErroApi(
                        400,
                        "DATA_INVALIDA",
                        "Escolha uma data e um horário válidos e futuros.");

        if (valor == null
                || !valor.isTextual()) {

            throw invalida;
        }

        LocalDateTime inicio;

        try {
            inicio =
                    LocalDateTime.parse(
                            valor.asText().trim());

        } catch (DateTimeParseException e) {
            throw invalida;
        }

        if (inicio.getSecond() != 0
                || inicio.getNano() != 0
                || !inicio.isAfter(Banco.agora())) {

            throw invalida;
        }

        return inicio;
    }
}