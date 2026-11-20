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

/** Acesso à tabela {@code agendamento}. */
public class AgendamentoRepositorio {

    /*
     * Usado pela H05 na área autenticada do cliente.
     */
    public record ItemAgendamentoCliente(
            int agendamentoId,
            String servicoNome,
            String profissionalNome,
            LocalDateTime inicio,
            LocalDateTime fim,
            String status) {
    }

    /*
     * CT11 - agenda diária do profissional.
     */
    public record AgendamentoDoDia(
            int agendamentoId,
            LocalDateTime inicio,
            LocalDateTime fim,
            String clienteNome,
            String clienteTelefone,
            String servicoNome) {
    }

    /*
     * Estrutura da develop para agendamentos do cliente.
     * Mantemos porque outros recursos/testes podem depender dela.
     */
    public record AgendamentoDoCliente(
            int agendamentoId,
            int servicoId,
            String servicoNome,
            String profissionalNome,
            String profissionalSlug,
            LocalDateTime inicio,
            LocalDateTime fim) {
    }

    /*
     * Estrutura antiga da develop usada pelo cancelamento.
     * Mantida para compatibilidade.
     */
    public record SituacaoDoAgendamento(
            String clienteTelefone,
            String status,
            LocalDateTime inicio) {
    }

    /**
     * Serializa alterações na agenda de um profissional.
     */
    public void travarAgendaDoProfissional(
            Connection c,
            int profissionalId) throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM profissional WHERE id = ? FOR UPDATE")) {

            ps.setInt(1, profissionalId);
            ps.executeQuery().close();
        }
    }

    /**
     * Verifica conflito de horário entre agendamentos confirmados.
     */
    public boolean existeConflito(
            Connection c,
            int profissionalId,
            LocalDateTime inicio,
            LocalDateTime fim) throws SQLException {

        String sql =
                "SELECT 1 FROM agendamento " +
                "WHERE profissional_id = ? " +
                "AND status = ? " +
                "AND inicio < ? " +
                "AND fim > ? " +
                "LIMIT 1";

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

    /**
     * CT11 - lista agendamentos confirmados do profissional
     * em uma determinada data.
     */
    public List<AgendamentoDoDia> listarConfirmadosDoDia(
            Connection c,
            int profissionalId,
            LocalDate dia) throws SQLException {

        String sql = """
                SELECT
                    a.id,
                    a.inicio,
                    a.fim,
                    c.nome AS cliente_nome,
                    c.telefone AS cliente_telefone,
                    s.nome AS servico_nome
                FROM agendamento a
                JOIN cliente c
                  ON c.id = a.cliente_id
                JOIN servico s
                  ON s.id = a.servico_id
                WHERE a.profissional_id = ?
                  AND a.status = ?
                  AND a.inicio >= ?
                  AND a.inicio < ?
                ORDER BY a.inicio, a.id
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, profissionalId);
            ps.setString(2, Agendamento.CONFIRMADO);
            ps.setObject(3, dia.atStartOfDay());
            ps.setObject(
                    4,
                    dia.plusDays(1).atStartOfDay());

            try (ResultSet rs = ps.executeQuery()) {

                List<AgendamentoDoDia> lista =
                        new ArrayList<>();

                while (rs.next()) {

                    lista.add(
                            new AgendamentoDoDia(
                                    rs.getInt("id"),
                                    rs.getObject(
                                            "inicio",
                                            LocalDateTime.class),
                                    rs.getObject(
                                            "fim",
                                            LocalDateTime.class),
                                    rs.getString(
                                            "cliente_nome"),
                                    rs.getString(
                                            "cliente_telefone"),
                                    rs.getString(
                                            "servico_nome")));
                }

                return lista;
            }
        }
    }

    /**
     * Método da develop mantido para compatibilidade.
     */
    public List<AgendamentoDoCliente>
    listarFuturosConfirmadosDoCliente(
            Connection c,
            int clienteId,
            LocalDateTime agora) throws SQLException {

        String sql = """
                SELECT
                    a.id,
                    a.servico_id,
                    s.nome AS servico_nome,
                    p.nome AS profissional_nome,
                    p.slug AS profissional_slug,
                    a.inicio,
                    a.fim
                FROM agendamento a
                JOIN servico s
                  ON s.id = a.servico_id
                JOIN profissional p
                  ON p.id = a.profissional_id
                WHERE a.cliente_id = ?
                  AND a.status = ?
                  AND a.inicio > ?
                ORDER BY a.inicio, a.id
                """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, clienteId);
            ps.setString(2, Agendamento.CONFIRMADO);
            ps.setObject(3, agora);

            try (ResultSet rs = ps.executeQuery()) {

                List<AgendamentoDoCliente> lista =
                        new ArrayList<>();

                while (rs.next()) {

                    lista.add(
                            new AgendamentoDoCliente(
                                    rs.getInt("id"),
                                    rs.getInt("servico_id"),
                                    rs.getString(
                                            "servico_nome"),
                                    rs.getString(
                                            "profissional_nome"),
                                    rs.getString(
                                            "profissional_slug"),
                                    rs.getObject(
                                            "inicio",
                                            LocalDateTime.class),
                                    rs.getObject(
                                            "fim",
                                            LocalDateTime.class)));
                }

                return lista;
            }
        }
    }

    /**
     * Grava um agendamento confirmado com lembrete pendente.
     */
    public Agendamento inserir(
            Connection c,
            int profissionalId,
            int servicoId,
            int clienteId,
            LocalDateTime inicio,
            LocalDateTime fim) throws SQLException {

        String sql = """
                INSERT INTO agendamento
                (
                    profissional_id,
                    servico_id,
                    cliente_id,
                    inicio,
                    fim,
                    status,
                    lembrete_status,
                    lembrete_registrado_em,
                    criado_em,
                    atualizado_em
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """;

        LocalDateTime agora =
                Banco.agora();

        try (PreparedStatement ps =
                     c.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, profissionalId);
            ps.setInt(2, servicoId);
            ps.setInt(3, clienteId);
            ps.setObject(4, inicio);
            ps.setObject(5, fim);
            ps.setString(
                    6,
                    Agendamento.CONFIRMADO);
            ps.setString(
                    7,
                    Agendamento.LEMBRETE_PENDENTE);
            ps.setObject(8, agora);
            ps.setObject(9, agora);

            ps.executeUpdate();

            try (ResultSet chaves =
                         ps.getGeneratedKeys()) {

                chaves.next();

                return new Agendamento(
                        chaves.getInt(1),
                        profissionalId,
                        servicoId,
                        clienteId,
                        inicio,
                        fim,
                        Agendamento.CONFIRMADO,
                        Agendamento.LEMBRETE_PENDENTE,
                        null,
                        agora,
                        agora);
            }
        }
    }

    /**
     * H05 - lista os agendamentos futuros do cliente autenticado.
     */
    public List<ItemAgendamentoCliente>
    listarFuturosDoCliente(
            Connection c,
            int clienteId) throws SQLException {

        String sql = """
                SELECT
                    a.id AS agendamento_id,
                    s.nome AS servico_nome,
                    p.nome AS profissional_nome,
                    a.inicio,
                    a.fim,
                    a.status
                FROM agendamento a
                JOIN servico s
                  ON s.id = a.servico_id
                JOIN profissional p
                  ON p.id = a.profissional_id
                WHERE a.cliente_id = ?
                  AND a.status = ?
                  AND a.inicio > ?
                ORDER BY a.inicio
                """;

        List<ItemAgendamentoCliente> itens =
                new ArrayList<>();

        try (PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setInt(1, clienteId);
            ps.setString(
                    2,
                    Agendamento.CONFIRMADO);
            ps.setObject(
                    3,
                    Banco.agora());

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    itens.add(
                            new ItemAgendamentoCliente(
                                    rs.getInt(
                                            "agendamento_id"),
                                    rs.getString(
                                            "servico_nome"),
                                    rs.getString(
                                            "profissional_nome"),
                                    rs.getObject(
                                            "inicio",
                                            LocalDateTime.class),
                                    rs.getObject(
                                            "fim",
                                            LocalDateTime.class),
                                    rs.getString(
                                            "status")));
                }
            }
        }

        return itens;
    }

    /**
     * H05 - busca agendamento pertencente ao cliente autenticado
     * e trava a linha para alteração.
     */
    public Optional<Agendamento>
    buscarPorIdEClienteParaAtualizar(
            Connection c,
            int agendamentoId,
            int clienteId) throws SQLException {

        String sql = """
                SELECT
                    id,
                    profissional_id,
                    servico_id,
                    cliente_id,
                    inicio,
                    fim,
                    status,
                    lembrete_status,
                    lembrete_registrado_em,
                    criado_em,
                    atualizado_em
                FROM agendamento
                WHERE id = ?
                  AND cliente_id = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setInt(1, agendamentoId);
            ps.setInt(2, clienteId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new Agendamento(
                                rs.getInt("id"),
                                rs.getInt(
                                        "profissional_id"),
                                rs.getInt(
                                        "servico_id"),
                                rs.getInt(
                                        "cliente_id"),
                                rs.getObject(
                                        "inicio",
                                        LocalDateTime.class),
                                rs.getObject(
                                        "fim",
                                        LocalDateTime.class),
                                rs.getString(
                                        "status"),
                                rs.getString(
                                        "lembrete_status"),
                                rs.getObject(
                                        "lembrete_registrado_em",
                                        LocalDateTime.class),
                                rs.getObject(
                                        "criado_em",
                                        LocalDateTime.class),
                                rs.getObject(
                                        "atualizado_em",
                                        LocalDateTime.class)));
            }
        }
    }

    /**
     * Método antigo da develop mantido para não quebrar
     * código/testes que eventualmente ainda o utilizem.
     */
    public Optional<SituacaoDoAgendamento>
    travarParaCancelar(
            Connection c,
            int agendamentoId) throws SQLException {

        String sql = """
                SELECT
                    c.telefone,
                    a.status,
                    a.inicio
                FROM agendamento a
                JOIN cliente c
                  ON c.id = a.cliente_id
                WHERE a.id = ?
                FOR UPDATE
                """;

        try (PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setInt(1, agendamentoId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new SituacaoDoAgendamento(
                                rs.getString("telefone"),
                                rs.getString("status"),
                                rs.getObject(
                                        "inicio",
                                        LocalDateTime.class)));
            }
        }
    }

    /**
     * Versão da develop.
     */
    public void cancelar(
            Connection c,
            int agendamentoId,
            LocalDateTime atualizadoEm)
            throws SQLException {

        String sql = """
                UPDATE agendamento
                SET status = ?,
                    atualizado_em = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setString(
                    1,
                    Agendamento.CANCELADO);
            ps.setObject(
                    2,
                    atualizadoEm);
            ps.setInt(
                    3,
                    agendamentoId);

            ps.executeUpdate();
        }
    }

    /**
     * H05 - cancela e devolve o momento da atualização.
     */
    public LocalDateTime cancelar(
            Connection c,
            int agendamentoId) throws SQLException {

        LocalDateTime agora =
                Banco.agora();

        cancelar(
                c,
                agendamentoId,
                agora);

        return agora;
    }
}