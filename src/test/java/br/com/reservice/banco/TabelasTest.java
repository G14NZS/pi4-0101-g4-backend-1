package br.com.reservice.banco;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.servico.ServicoRepositorio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Erros das fichas CT14 (profissional) e CT15 (servico): recusas do próprio banco. */
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
}
