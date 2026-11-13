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
