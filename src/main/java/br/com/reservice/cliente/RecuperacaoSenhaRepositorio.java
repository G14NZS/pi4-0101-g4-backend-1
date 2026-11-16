package br.com.reservice.cliente;

import br.com.reservice.banco.Banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

public class RecuperacaoSenhaRepositorio {

    public int inserirPendente(
            Connection c,
            int clienteId) throws SQLException {

        String sql = """
                INSERT INTO recuperacao_senha
                (cliente_id, criado_em)
                VALUES (?, ?)
                """;

        try (PreparedStatement ps =
                     c.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, clienteId);
            ps.setObject(2, Banco.agora());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void definirCodigo(
            Connection c,
            int id,
            String codigoHash,
            LocalDateTime expiraEm) throws SQLException {

        String sql = """
                UPDATE recuperacao_senha
                SET codigo_hash = ?, expira_em = ?
                WHERE id = ?
                  AND utilizado_em IS NULL
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codigoHash);
            ps.setObject(2, expiraEm);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public Optional<RecuperacaoSenha> buscarValidaMaisRecente(
            Connection c,
            int clienteId) throws SQLException {

        String sql = """
                SELECT id, cliente_id, codigo_hash,
                       expira_em, utilizado_em, criado_em
                FROM recuperacao_senha
                WHERE cliente_id = ?
                  AND codigo_hash IS NOT NULL
                  AND utilizado_em IS NULL
                ORDER BY id DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clienteId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(new RecuperacaoSenha(
                        rs.getInt("id"),
                        rs.getInt("cliente_id"),
                        rs.getString("codigo_hash"),
                        rs.getObject("expira_em", LocalDateTime.class),
                        rs.getObject("utilizado_em", LocalDateTime.class),
                        rs.getObject("criado_em", LocalDateTime.class)));
            }
        }
    }

    public void marcarUtilizada(
            Connection c,
            int id) throws SQLException {

        String sql = """
                UPDATE recuperacao_senha
                SET utilizado_em = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, Banco.agora());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }
}