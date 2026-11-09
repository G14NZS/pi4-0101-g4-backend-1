package br.com.reservice.servico;

import br.com.reservice.banco.Banco;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.profissional.Profissional;
import br.com.reservice.profissional.ProfissionalRepositorio;
import br.com.reservice.sessao.Tokens;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;

/** CT2 cadastrarServico ({@code POST /servicos}) e CT3 listarServicos ({@code GET /profissionais/{slug}/servicos}). */
public class ServicoRecurso {

    static final int DURACAO_MINIMA = 15;
    static final int DURACAO_MAXIMA = 480;
    static final int NOME_TAMANHO_MAXIMO = 100;
    /** Limite de DECIMAL(10,2) na coluna {@code servico.preco}. */
    static final BigDecimal PRECO_MAXIMO = new BigDecimal("99999999.99");

    public record ServicoCadastrado(
            int servicoId, String servicoNome, int duracaoMinutos, BigDecimal preco, LocalDateTime criadoEm) {
    }

    public record ItemServico(int servicoId, String servicoNome, int duracaoMinutos, BigDecimal preco) {
    }

    public record ServicosDoProfissional(
            String profissionalNome, String profissionalTelefone, List<ItemServico> servicos) {
    }

    private final ServicoRepositorio servicos;
    private final ProfissionalRepositorio profissionais;
    private final Tokens tokens;

    public ServicoRecurso(ServicoRepositorio servicos, ProfissionalRepositorio profissionais, Tokens tokens) {
        this.servicos = servicos;
        this.profissionais = profissionais;
        this.tokens = tokens;
    }

    public Resposta cadastrarServico(Requisicao requisicao) throws SQLException {
        int profissionalId = tokens.profissionalAutenticado(requisicao);
        JsonNode corpo = requisicao.corpoJson();

        String nome = lerNome(corpo.get("servicoNome"));
        int duracao = lerDuracao(corpo.get("duracaoMinutos"));
        BigDecimal preco = lerPreco(corpo.get("preco"));

        Servico s;
        try {
            s = servicos.inserir(profissionalId, nome, duracao, preco);
        } catch (SQLIntegrityConstraintViolationException e) {
            if (e.getErrorCode() == Banco.ERRO_VALOR_DUPLICADO) {
                throw new ErroApi(409, "SERVICO_DUPLICADO", "Você já tem um serviço com esse nome.");
            }
            if (e.getErrorCode() == Banco.ERRO_CHAVE_ESTRANGEIRA) {
                // Token válido de um profissional que não existe mais.
                throw ErroApi.naoAutenticado();
            }
            throw e;
        }
        return Resposta.criado(new ServicoCadastrado(s.id(), s.nome(), s.duracaoMinutos(), s.preco(), s.criadoEm()));
    }

    public Resposta listarServicos(Requisicao requisicao) throws SQLException {
        Profissional p = profissionais.buscarPorSlug(requisicao.parametro(0))
                .orElseThrow(() -> new ErroApi(404, "PROFISSIONAL_NAO_ENCONTRADO",
                        "Não encontramos esse profissional."));
        List<ItemServico> itens = servicos.listarDoProfissional(p.id()).stream()
                .map(s -> new ItemServico(s.id(), s.nome(), s.duracaoMinutos(), s.preco()))
                .toList();
        return Resposta.ok(new ServicosDoProfissional(p.nome(), p.telefone(), itens));
    }

    private static String lerNome(JsonNode valor) {
        if (valor == null || !valor.isTextual() || valor.asText().isBlank()) {
            throw ErroApi.dadosInvalidos("Informe o nome do serviço.");
        }
        String nome = valor.asText().trim();
        if (nome.length() > NOME_TAMANHO_MAXIMO) {
            throw ErroApi.dadosInvalidos("O nome do serviço pode ter até " + NOME_TAMANHO_MAXIMO + " caracteres.");
        }
        return nome;
    }

    private static int lerDuracao(JsonNode valor) {
        if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()
                || valor.asInt() < DURACAO_MINIMA || valor.asInt() > DURACAO_MAXIMA) {
            throw ErroApi.dadosInvalidos("A duração deve ser um número inteiro de "
                    + DURACAO_MINIMA + " a " + DURACAO_MAXIMA + " minutos.");
        }
        return valor.asInt();
    }

    private static BigDecimal lerPreco(JsonNode valor) {
        if (valor == null || !valor.isNumber()) {
            throw ErroApi.dadosInvalidos("Informe o preço do serviço.");
        }
        BigDecimal preco = valor.decimalValue();
        if (preco.signum() < 0) {
            throw ErroApi.dadosInvalidos("O preço não pode ser negativo.");
        }
        if (preco.stripTrailingZeros().scale() > 2 || preco.compareTo(PRECO_MAXIMO) > 0) {
            throw ErroApi.dadosInvalidos("Informe um preço válido, com até dois dígitos de centavos.");
        }
        return preco.setScale(2);
    }
}
