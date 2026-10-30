package br.com.reservice.banco;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.servico.ServicoRepositorio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Erros das fichas CT14 a CT18: recusas do próprio banco. */
class TabelasTest extends TesteIntegracao {

    @Test
    void ct14EmailRepetidoEhRecusado() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");

        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> criarProfissional("Outra", "ana@exemplo.com", "x", "+5519988887777", "outra"));
        assertEquals(Banco.ERRO_VALOR_DUPLICADO, e.getErrorCode());
    }

    @Test
    void ct14SlugRepetidoEhRecusado() throws Exception {
        criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");

        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> criarProfissional("Outra", "outra@exemplo.com", "x", "+5519988887777", "ana-unhas"));
        assertEquals(Banco.ERRO_VALOR_DUPLICADO, e.getErrorCode());
    }

    @Test
    void ct15ProfissionalInexistenteEhRecusado() {
        ServicoRepositorio servicos = new ServicoRepositorio(banco);

        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> servicos.inserir(999_999, "Manicure", 45, new BigDecimal("35.00")));
        assertEquals(Banco.ERRO_CHAVE_ESTRANGEIRA, e.getErrorCode());
    }

    @Test
    void ct15ParProfissionalENomeRepetidoEhRecusado() throws Exception {
        int ana = criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        ServicoRepositorio servicos = new ServicoRepositorio(banco);
        servicos.inserir(ana, "Manicure", 45, new BigDecimal("35.00"));

        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> servicos.inserir(ana, "Manicure", 30, new BigDecimal("20.00")));
        assertEquals(Banco.ERRO_VALOR_DUPLICADO, e.getErrorCode());
    }

    @Test
    void ct16ProfissionalInexistenteEhRecusado() {
        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> executar("INSERT INTO horario_atendimento (profissional_id, dia_semana, hora_inicio, hora_fim)"
                        + " VALUES (999999, 'terca', '09:00', '18:00')"));
        assertEquals(Banco.ERRO_CHAVE_ESTRANGEIRA, e.getErrorCode());
    }

    @Test
    void ct16DiaSemanaForaDaListaEhRecusado() throws Exception {
        int ana = criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");

        assertThrows(SQLException.class,
                () -> executar("INSERT INTO horario_atendimento (profissional_id, dia_semana, hora_inicio, hora_fim)"
                        + " VALUES (" + ana + ", 'feriado', '09:00', '18:00')"));
    }

    @Test
    void ct17TelefoneRepetidoEhRecusado() throws Exception {
        executar("INSERT INTO cliente (nome, telefone, criado_em) VALUES ('Maria', '+5519988887777', NOW())");

        var e = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> executar("INSERT INTO cliente (nome, telefone, criado_em) VALUES ('Outra', '+5519988887777', NOW())"));
        assertEquals(Banco.ERRO_VALOR_DUPLICADO, e.getErrorCode());
    }

    @Test
    void ct18ReferenciaInexistenteEhRecusada() {
        var e = assertThrows(SQLIntegrityConstraintViolationException.class, () -> executar(agendamento(
                "999999, 999999, 999999", "'2030-01-07 10:00:00', '2030-01-07 10:45:00'", "'confirmado', 'pendente'")));
        assertEquals(Banco.ERRO_CHAVE_ESTRANGEIRA, e.getErrorCode());
    }

    @Test
    void ct18FimQueNaoEhDepoisDoInicioEhRecusado() throws Exception {
        String ids = idsValidos();

        assertThrows(SQLException.class, () -> executar(agendamento(
                ids, "'2030-01-07 10:00:00', '2030-01-07 10:00:00'", "'confirmado', 'pendente'")));
    }

    @Test
    void ct18StatusForaDaListaEhRecusado() throws Exception {
        String ids = idsValidos();
        String periodo = "'2030-01-07 10:00:00', '2030-01-07 10:45:00'";

        assertThrows(SQLException.class, () -> executar(agendamento(ids, periodo, "'remarcado', 'pendente'")));
        assertThrows(SQLException.class, () -> executar(agendamento(ids, periodo, "'confirmado', 'lido'")));
    }

    /** "profissional_id, servico_id, cliente_id" de linhas que existem. */
    private String idsValidos() throws Exception {
        int ana = criarProfissional("Ana", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        int servico = new ServicoRepositorio(banco).inserir(ana, "Manicure", 45, new BigDecimal("35.00")).id();
        executar("INSERT INTO cliente (nome, telefone, criado_em) VALUES ('Maria', '+5519988887777', NOW())");
        try (Connection c = banco.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM cliente")) {
            rs.next();
            return ana + ", " + servico + ", " + rs.getInt(1);
        }
    }

    private static String agendamento(String ids, String periodo, String status) {
        return "INSERT INTO agendamento (profissional_id, servico_id, cliente_id, inicio, fim, status,"
                + " lembrete_status, criado_em, atualizado_em) VALUES (" + ids + ", " + periodo + ", " + status
                + ", NOW(), NOW())";
    }

    private void executar(String sql) throws SQLException {
        try (Connection c = banco.conectar(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
