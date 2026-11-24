package br.com.reservice.servico;

import br.com.reservice.banco.Banco;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela {@code servico} (CT15). */
public class ServicoRepositorio {

    private final Banco banco;

    public ServicoRepositorio(Banco banco) {
        this.banco = banco;
    }

    /**
     * Grava um serviço. O banco recusa profissional inexistente e o par (profissional_id, nome)
     * repetido com {@link java.sql.SQLIntegrityConstraintViolationException} (CT15).
     */
    public Servico inserir(int profissionalId, String nome, int duracaoMinutos, BigDecimal preco)
            throws SQLException {
        String sql = "INSERT INTO servico (profissional_id, nome, duracao_minutos, preco, criado_em)"
                + " VALUES (?, ?, ?, ?, ?)";
        LocalDateTime criadoEm = Banco.agora();
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, nome);
            ps.setInt(3, duracaoMinutos);
            ps.setBigDecimal(4, preco);
            ps.setObject(5, criadoEm);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return new Servico(chaves.getInt(1), profissionalId, nome, duracaoMinutos, preco, criadoEm);
            }
        }
    }

    public List<Servico> listarDoProfissional(int profissionalId) throws SQLException {
        String sql = "SELECT id, profissional_id, nome, duracao_minutos, preco, criado_em"
                + " FROM servico WHERE profissional_id = ? ORDER BY nome, id";
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Servico> servicos = new ArrayList<>();
                while (rs.next()) {
                    servicos.add(ler(rs));
                }
                return servicos;
            }
        }
    }

    /** Busca um serviço usando a conexão (e a transação) de quem chama. */
    public Optional<Servico> buscarPorId(Connection c, int id) throws SQLException {
        String sql = "SELECT id, profissional_id, nome, duracao_minutos, preco, criado_em FROM servico WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
            }
        }
    }

    private static Servico ler(ResultSet rs) throws SQLException {
        return new Servico(
                rs.getInt("id"),
                rs.getInt("profissional_id"),
                rs.getString("nome"),
                rs.getInt("duracao_minutos"),
                rs.getBigDecimal("preco"),
                rs.getObject("criado_em", LocalDateTime.class));
    }
}
