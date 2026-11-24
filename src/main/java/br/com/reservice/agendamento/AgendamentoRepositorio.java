package br.com.reservice.agendamento;

import br.com.reservice.banco.Banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

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
