package br.com.reservice.cliente;

import br.com.reservice.banco.Banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/** Acesso à tabela {@code cliente} (CT17). Os métodos usam a conexão (e a transação) de quem chama. */
public class ClienteRepositorio {

    /** Formato +55DDDNÚMERO: DDD sem zero e número de 8 (fixo) ou 9 (celular) dígitos. */
    private static final Pattern TELEFONE = Pattern.compile("\\+55[1-9]{2}\\d{8,9}");

    public static boolean telefoneValido(String telefone) {
        return telefone != null && TELEFONE.matcher(telefone).matches();
    }

    public Optional<Cliente> buscarPorTelefone(Connection c, String telefone) throws SQLException {
        String sql = "SELECT id, nome, telefone, criado_em FROM cliente WHERE telefone = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, telefone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Cliente(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getString("telefone"),
                        rs.getObject("criado_em", LocalDateTime.class)));
            }
        }
    }

    /**
     * Grava um cliente. Telefone repetido é recusado pelo banco
     * ({@link java.sql.SQLIntegrityConstraintViolationException}), como diz o CT17.
     */
    public Cliente inserir(Connection c, String nome, String telefone) throws SQLException {
        String sql = "INSERT INTO cliente (nome, telefone, criado_em) VALUES (?, ?, ?)";
        LocalDateTime criadoEm = Banco.agora();
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, telefone);
            ps.setObject(3, criadoEm);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return new Cliente(chaves.getInt(1), nome, telefone, criadoEm);
            }
        }
    }
}
