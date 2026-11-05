package br.com.reservice.agendamento;

import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.Cliente;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.horario.HorarioRepositorio;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.servico.Servico;
import br.com.reservice.servico.ServicoRepositorio;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** CT7 criarAgendamento ({@code POST /agendamentos}). Sem login: o cliente é identificado pelo telefone. */
public class AgendamentoRecurso {

    /** Tamanho da coluna {@code cliente.nome}. */
    static final int NOME_TAMANHO_MAXIMO = 120;

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

    private record Pedido(int servicoId, LocalDateTime inicio, String telefone, String nome) {
    }

    private final Banco banco;
    private final ServicoRepositorio servicos;
    private final HorarioRepositorio horarios;
    private final ClienteRepositorio clientes;
    private final AgendamentoRepositorio agendamentos;

    public AgendamentoRecurso(Banco banco, ServicoRepositorio servicos, HorarioRepositorio horarios,
                              ClienteRepositorio clientes, AgendamentoRepositorio agendamentos) {
        this.banco = banco;
        this.servicos = servicos;
        this.horarios = horarios;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
    }

    public Resposta criarAgendamento(Requisicao requisicao) throws SQLException {
        JsonNode corpo = requisicao.corpoJson();
        Pedido pedido = new Pedido(
                lerServicoId(corpo.get("servicoId")),
                lerInicio(corpo.get("inicio")),
                lerTelefone(corpo.get("clienteTelefone")),
                lerNome(corpo.get("clienteNome")));

        try (Connection c = banco.conectar()) {
            // READ COMMITTED: cada leitura vê o que já foi confirmado, inclusive por quem segurava a trava antes.
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                AgendamentoCriado criado = gravar(c, pedido);
                c.commit();
                return Resposta.criado(criado);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Confere tudo de novo dentro da transação: o que o Frontend viu antes (CT3, CT5, CT6) pode ter mudado. */
    private AgendamentoCriado gravar(Connection c, Pedido pedido) throws SQLException {
        Servico servico = servicos.buscarPorId(c, pedido.servicoId())
                .orElseThrow(() -> new ErroApi(404, "SERVICO_NAO_ENCONTRADO", "Não encontramos esse serviço."));
        agendamentos.travarAgendaDoProfissional(c, servico.profissionalId());

        Optional<Cliente> existente = clientes.buscarPorTelefone(c, pedido.telefone());
        if (existente.isEmpty() && pedido.nome() == null) {
            throw new ErroApi(400, "NOME_OBRIGATORIO", "Informe seu nome para o primeiro agendamento.");
        }

        LocalDateTime inicio = pedido.inicio();
        LocalDateTime fim = inicio.plusMinutes(servico.duracaoMinutos());
        if (!cabeNaGrade(c, servico.profissionalId(), inicio, fim)
                || agendamentos.existeConflito(c, servico.profissionalId(), inicio, fim)) {
            throw new ErroApi(409, "HORARIO_INDISPONIVEL",
                    "Esse horário não está mais disponível. Escolha outro.");
        }

        Cliente cliente = existente.isPresent() ? existente.get() : cadastrarCliente(c, pedido);
        Agendamento a = agendamentos.inserir(c, servico.profissionalId(), servico.id(), cliente.id(), inicio, fim);
        return new AgendamentoCriado(a.id(), servico.id(), servico.nome(), a.inicio(), a.fim(),
                cliente.id(), cliente.nome(), a.status());
    }

    private boolean cabeNaGrade(Connection c, int profissionalId, LocalDateTime inicio, LocalDateTime fim)
            throws SQLException {
        if (!fim.toLocalDate().equals(inicio.toLocalDate())) {
            return false;
        }
        return horarios.listarDoDia(c, profissionalId, inicio.getDayOfWeek()).stream()
                .anyMatch(faixa -> faixa.comporta(inicio.toLocalTime(), fim.toLocalTime()));
    }

    /** Cria o cliente novo; se outro pedido acabou de cadastrar o mesmo telefone, usa esse cadastro. */
    private Cliente cadastrarCliente(Connection c, Pedido pedido) throws SQLException {
        try {
            return clientes.inserir(c, pedido.nome(), pedido.telefone());
        } catch (SQLIntegrityConstraintViolationException e) {
            if (e.getErrorCode() != Banco.ERRO_VALOR_DUPLICADO) {
                throw e;
            }
            Optional<Cliente> cadastrado = clientes.buscarPorTelefone(c, pedido.telefone());
            if (cadastrado.isEmpty()) {
                throw e;
            }
            return cadastrado.get();
        }
    }

    private static int lerServicoId(JsonNode valor) {
        if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()) {
            throw ErroApi.dadosInvalidos("Escolha o serviço.");
        }
        return valor.asInt();
    }

    /** Data e hora sem fuso, no horário de Brasília, em minuto cheio e depois de agora. */
    private static LocalDateTime lerInicio(JsonNode valor) {
        ErroApi invalida = new ErroApi(400, "DATA_INVALIDA", "Escolha uma data e um horário válidos e futuros.");
        if (valor == null || !valor.isTextual()) {
            throw invalida;
        }
        LocalDateTime inicio;
        try {
            inicio = LocalDateTime.parse(valor.asText().trim());
        } catch (DateTimeParseException e) {
            throw invalida;
        }
        if (inicio.getSecond() != 0 || inicio.getNano() != 0 || !inicio.isAfter(Banco.agora())) {
            throw invalida;
        }
        return inicio;
    }

    private static String lerTelefone(JsonNode valor) {
        String telefone = valor != null && valor.isTextual() ? valor.asText().trim() : null;
        if (!ClienteRepositorio.telefoneValido(telefone)) {
            throw new ErroApi(400, "TELEFONE_INVALIDO",
                    "Informe o telefone no formato +55 com DDD, por exemplo +5519988887777.");
        }
        return telefone;
    }

    /** Nome enviado, sem espaços nas pontas, ou {@code null} quando não veio (só é exigido de cliente novo). */
    private static String lerNome(JsonNode valor) {
        if (valor == null || valor.isNull() || (valor.isTextual() && valor.asText().isBlank())) {
            return null;
        }
        if (!valor.isTextual()) {
            throw ErroApi.dadosInvalidos("Informe seu nome.");
        }
        String nome = valor.asText().trim();
        if (nome.length() > NOME_TAMANHO_MAXIMO) {
            throw ErroApi.dadosInvalidos("O nome pode ter até " + NOME_TAMANHO_MAXIMO + " caracteres.");
        }
        return nome;
    }
}
