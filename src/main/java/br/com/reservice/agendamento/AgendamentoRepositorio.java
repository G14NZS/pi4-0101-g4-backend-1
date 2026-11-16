package br.com.reservice.agendamento;

import br.com.reservice.banco.Banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela {@code agendamento} (CT18). Os métodos usam a conexão (e a transação) de quem chama. */
public class AgendamentoRepositorio {

    /**
     * Trava a linha do profissional até o fim da transação. Quem agenda com o mesmo profissional
     * espera aqui, então as conferências de grade e de conflito rodam uma de cada vez por agenda.
     */
    public void travarAgendaDoProfissional(Connection c, int profissionalId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM profissional WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, profissionalId);
            ps.executeQuery().close();
        }
    }

    /** Diz se algum agendamento confirmado do profissional se sobrepõe ao período [inicio, fim). */
    public boolean existeConflito(Connection c, int profissionalId, LocalDateTime inicio, LocalDateTime fim)
            throws SQLException {
        String sql = "SELECT 1 FROM agendamento WHERE profissional_id = ? AND status = ?"
                + " AND inicio < ? AND fim > ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, Agendamento.CONFIRMADO);
            ps.setObject(3, fim);
            ps.setObject(4, inicio);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Agendamento do dia já com o nome e o telefone do cliente e o nome do serviço (CT11). */
    public record AgendamentoDoDia(
            int agendamentoId,
            LocalDateTime inicio,
            LocalDateTime fim,
            String clienteNome,
            String clienteTelefone,
            String servicoNome) {
    }

    /**
     * Agendamentos confirmados do profissional que começam no dia, em ordem de início.
     * Uma consulta só, com JOIN, e o filtro por intervalo em {@code inicio} usa o índice
     * {@code ix_agendamento_profissional_inicio}.
     */
    public List<AgendamentoDoDia> listarConfirmadosDoDia(Connection c, int profissionalId, LocalDate dia)
            throws SQLException {
        String sql = "SELECT a.id, a.inicio, a.fim, c.nome AS cliente_nome, c.telefone AS cliente_telefone,"
                + " s.nome AS servico_nome"
                + " FROM agendamento a"
                + " JOIN cliente c ON c.id = a.cliente_id"
                + " JOIN servico s ON s.id = a.servico_id"
                + " WHERE a.profissional_id = ? AND a.status = ? AND a.inicio >= ? AND a.inicio < ?"
                + " ORDER BY a.inicio, a.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, Agendamento.CONFIRMADO);
            ps.setObject(3, dia.atStartOfDay());
            ps.setObject(4, dia.plusDays(1).atStartOfDay());
            try (ResultSet rs = ps.executeQuery()) {
                List<AgendamentoDoDia> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new AgendamentoDoDia(
                            rs.getInt("id"),
                            rs.getObject("inicio", LocalDateTime.class),
                            rs.getObject("fim", LocalDateTime.class),
                            rs.getString("cliente_nome"),
                            rs.getString("cliente_telefone"),
                            rs.getString("servico_nome")));
                }
                return lista;
            }
        }
    }

    /** Agendamento do cliente já com o serviço e o profissional (CT8). */
    public record AgendamentoDoCliente(
            int agendamentoId,
            int servicoId,
            String servicoNome,
            String profissionalNome,
            String profissionalSlug,
            LocalDateTime inicio,
            LocalDateTime fim) {
    }

    /** Agendamentos confirmados do cliente que começam depois de {@code agora}, em ordem de início (CT8). */
    public List<AgendamentoDoCliente> listarFuturosConfirmadosDoCliente(Connection c, int clienteId,
                                                                        LocalDateTime agora) throws SQLException {
        String sql = "SELECT a.id, a.servico_id, s.nome AS servico_nome, p.nome AS profissional_nome,"
                + " p.slug AS profissional_slug, a.inicio, a.fim"
                + " FROM agendamento a"
                + " JOIN servico s ON s.id = a.servico_id"
                + " JOIN profissional p ON p.id = a.profissional_id"
                + " WHERE a.cliente_id = ? AND a.status = ? AND a.inicio > ?"
                + " ORDER BY a.inicio, a.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clienteId);
            ps.setString(2, Agendamento.CONFIRMADO);
            ps.setObject(3, agora);
            try (ResultSet rs = ps.executeQuery()) {
                List<AgendamentoDoCliente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new AgendamentoDoCliente(
                            rs.getInt("id"),
                            rs.getInt("servico_id"),
                            rs.getString("servico_nome"),
                            rs.getString("profissional_nome"),
                            rs.getString("profissional_slug"),
                            rs.getObject("inicio", LocalDateTime.class),
                            rs.getObject("fim", LocalDateTime.class)));
                }
                return lista;
            }
        }
    }

    /** Estado do agendamento que o cancelamento precisa conferir (CT9). */
    public record SituacaoDoAgendamento(String clienteTelefone, String status, LocalDateTime inicio) {
    }

    /**
     * Lê o agendamento e trava a linha até o fim da transação, para que dois cancelamentos
     * do mesmo agendamento rodem um de cada vez.
     */
    public Optional<SituacaoDoAgendamento> travarParaCancelar(Connection c, int agendamentoId)
            throws SQLException {
        String sql = "SELECT c.telefone, a.status, a.inicio FROM agendamento a"
                + " JOIN cliente c ON c.id = a.cliente_id WHERE a.id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, agendamentoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SituacaoDoAgendamento(
                        rs.getString("telefone"),
                        rs.getString("status"),
                        rs.getObject("inicio", LocalDateTime.class)));
            }
        }
    }

    /** Muda o status para cancelado e grava {@code atualizado_em}. */
    public void cancelar(Connection c, int agendamentoId, LocalDateTime atualizadoEm) throws SQLException {
        String sql = "UPDATE agendamento SET status = ?, atualizado_em = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Agendamento.CANCELADO);
            ps.setObject(2, atualizadoEm);
            ps.setInt(3, agendamentoId);
            ps.executeUpdate();
        }
    }

    /** Lembrete a enviar, já com o cliente, o serviço e o profissional (CT12). */
    public record LembretePendente(
            int agendamentoId,
            String clienteNome,
            String clienteTelefone,
            String servicoNome,
            String profissionalNome,
            LocalDateTime inicio) {
    }

    /**
     * Agendamentos confirmados com lembrete pendente e início na janela {@code (agora, ate]}, em ordem de início.
     * O que começa exatamente agora já não recebe lembrete; o que começa exatamente em {@code ate} recebe.
     */
    public List<LembretePendente> listarLembretesPendentes(Connection c, LocalDateTime agora, LocalDateTime ate)
            throws SQLException {
        String sql = "SELECT a.id, c.nome AS cliente_nome, c.telefone AS cliente_telefone,"
                + " s.nome AS servico_nome, p.nome AS profissional_nome, a.inicio"
                + " FROM agendamento a"
                + " JOIN cliente c ON c.id = a.cliente_id"
                + " JOIN servico s ON s.id = a.servico_id"
                + " JOIN profissional p ON p.id = a.profissional_id"
                + " WHERE a.status = ? AND a.lembrete_status = ? AND a.inicio > ? AND a.inicio <= ?"
                + " ORDER BY a.inicio, a.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Agendamento.CONFIRMADO);
            ps.setString(2, Agendamento.LEMBRETE_PENDENTE);
            ps.setObject(3, agora);
            ps.setObject(4, ate);
            try (ResultSet rs = ps.executeQuery()) {
                List<LembretePendente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new LembretePendente(
                            rs.getInt("id"),
                            rs.getString("cliente_nome"),
                            rs.getString("cliente_telefone"),
                            rs.getString("servico_nome"),
                            rs.getString("profissional_nome"),
                            rs.getObject("inicio", LocalDateTime.class)));
                }
                return lista;
            }
        }
    }

    /**
     * Grava o resultado do lembrete só se ele ainda estiver pendente, num único UPDATE: dois registros
     * ao mesmo tempo para o mesmo agendamento não passam os dois. Devolve se gravou.
     */
    public boolean registrarLembrete(Connection c, int agendamentoId, String resultado, LocalDateTime registradoEm)
            throws SQLException {
        String sql = "UPDATE agendamento SET lembrete_status = ?, lembrete_registrado_em = ?"
                + " WHERE id = ? AND lembrete_status = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, resultado);
            ps.setObject(2, registradoEm);
            ps.setInt(3, agendamentoId);
            ps.setString(4, Agendamento.LEMBRETE_PENDENTE);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean existe(Connection c, int agendamentoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM agendamento WHERE id = ?")) {
            ps.setInt(1, agendamentoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Grava um agendamento confirmado, com o lembrete pendente. */
    public Agendamento inserir(Connection c, int profissionalId, int servicoId, int clienteId,
                               LocalDateTime inicio, LocalDateTime fim) throws SQLException {
        String sql = "INSERT INTO agendamento (profissional_id, servico_id, cliente_id, inicio, fim, status,"
                + " lembrete_status, lembrete_registrado_em, criado_em, atualizado_em)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)";
        LocalDateTime agora = Banco.agora();
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, profissionalId);
            ps.setInt(2, servicoId);
            ps.setInt(3, clienteId);
            ps.setObject(4, inicio);
            ps.setObject(5, fim);
            ps.setString(6, Agendamento.CONFIRMADO);
            ps.setString(7, Agendamento.LEMBRETE_PENDENTE);
            ps.setObject(8, agora);
            ps.setObject(9, agora);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return new Agendamento(chaves.getInt(1), profissionalId, servicoId, clienteId, inicio, fim,
                        Agendamento.CONFIRMADO, Agendamento.LEMBRETE_PENDENTE, null, agora, agora);
            }
        }
    }
}
