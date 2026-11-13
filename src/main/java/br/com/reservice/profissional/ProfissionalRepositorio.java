package br.com.reservice.profissional;

import br.com.reservice.banco.Banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/** Acesso à tabela {@code profissional} (CT14). */
public class ProfissionalRepositorio {

    private static final String COLUNAS = "id, nome, email, senha_hash, telefone, slug, criado_em";

    private final Banco banco;

    public ProfissionalRepositorio(Banco banco) {
        this.banco = banco;
    }

    public Optional<Profissional> buscarPorEmail(String email) throws SQLException {
        return buscarUm("SELECT " + COLUNAS + " FROM profissional WHERE email = ?", email);
    }

    public Optional<Profissional> buscarPorSlug(String slug) throws SQLException {
        return buscarUm("SELECT " + COLUNAS + " FROM profissional WHERE slug = ?", slug);
    }

    /**
     * Grava um profissional e devolve o id gerado. E-mail ou slug repetido é recusado pelo banco
     * ({@link java.sql.SQLIntegrityConstraintViolationException}), como diz o CT14.
     */
    public int inserir(String nome, String email, String senhaHash, String telefone, String slug)
            throws SQLException {
        String sql = "INSERT INTO profissional (nome, email, senha_hash, telefone, slug, criado_em)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, email);
            ps.setString(3, senhaHash);
            ps.setString(4, telefone);
            ps.setString(5, slug);
            ps.setObject(6, Banco.agora());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    private Optional<Profissional> buscarUm(String sql, String valor) throws SQLException {
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Profissional(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getString("email"),
                        rs.getString("senha_hash"),
                        rs.getString("telefone"),
                        rs.getString("slug"),
                        rs.getObject("criado_em", LocalDateTime.class)));
            }
        }
    }
}
