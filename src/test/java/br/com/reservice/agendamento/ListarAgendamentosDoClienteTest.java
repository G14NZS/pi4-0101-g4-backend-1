package br.com.reservice.agendamento;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.servico.ServicoRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT8 listarAgendamentosDoCliente, lendo as tabelas CT17 (cliente) e CT18 (agendamento). */
class ListarAgendamentosDoClienteTest extends TesteIntegracao {

    private static final String MARIA = "+5519988887777";
    private static final String JOANA = "+5519911112222";

    private final AgendamentoRepositorio agendamentos = new AgendamentoRepositorio();

    private int anaId;
    private int biaId;
    private int manicureId;
    private int escovaDaBiaId;
    private int mariaId;
    private int joanaId;
    /** Uma semana à frente, em hora cheia, para os agendamentos "futuros". */
    private LocalDateTime semanaQueVem;

    @BeforeEach
    void preparar() throws Exception {
        anaId = criarProfissional("Ana Beatriz Costa", "ana@exemplo.com", "x", "+5519999990000", "ana-unhas");
        biaId = criarProfissional("Bia Lima", "bia@exemplo.com", "x", "+5519999991111", "bia-cabelos");
        ServicoRepositorio servicos = new ServicoRepositorio(banco);
        manicureId = servicos.inserir(anaId, "Manicure simples", 45, new BigDecimal("35.00")).id();
        escovaDaBiaId = servicos.inserir(biaId, "Escova", 30, new BigDecimal("50.00")).id();
        ClienteRepositorio clientes = new ClienteRepositorio();
        try (Connection c = banco.conectar()) {
            mariaId = clientes.inserir(c, "Maria Souza", MARIA).id();
            joanaId = clientes.inserir(c, "Joana Lima", JOANA).id();
        }
        semanaQueVem = Banco.agora().plusDays(7).withHour(10).withMinute(0).withSecond(0);
    }

    /** Grava direto no banco: assim dá para usar datas passadas. */
    private int agendar(int profissionalId, int servicoId, int clienteId, LocalDateTime inicio, int minutos)
            throws Exception {
        try (Connection c = banco.conectar()) {
            return agendamentos.inserir(c, profissionalId, servicoId, clienteId, inicio, inicio.plusMinutes(minutos))
                    .id();
        }
    }

    private void cancelarNoBanco(int agendamentoId) throws Exception {
        try (Connection c = banco.conectar();
             PreparedStatement ps = c.prepareStatement("UPDATE agendamento SET status = 'cancelado' WHERE id = ?")) {
            ps.setInt(1, agendamentoId);
            ps.executeUpdate();
        }
    }

    private HttpResponse<String> listar(String telefone) throws Exception {
        return get("/agendamentos/do-cliente?clienteTelefone=" + URLEncoder.encode(telefone, StandardCharsets.UTF_8));
    }

    @Test
    void devolveSoOsFuturosConfirmadosDoClienteEmOrdemDeInicio() throws Exception {
        // Gravados fora de ordem, para provar a ordenação.
        int depois = agendar(biaId, escovaDaBiaId, mariaId, semanaQueVem.plusDays(1), 30);
        int antes = agendar(anaId, manicureId, mariaId, semanaQueVem, 45);
        // Não podem aparecer:
        agendar(anaId, manicureId, mariaId, Banco.agora().minusDays(1), 45);           // já passou
        agendar(anaId, manicureId, mariaId, Banco.agora().minusMinutes(10), 45);       // começou e não terminou
        cancelarNoBanco(agendar(anaId, manicureId, mariaId, semanaQueVem.plusHours(2), 45)); // cancelado
        agendar(anaId, manicureId, joanaId, semanaQueVem.plusHours(4), 45);            // de outro cliente

        HttpResponse<String> r = listar(MARIA);

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(1, corpo.size(), "só os campos da ficha: " + corpo);
        JsonNode lista = corpo.get("agendamentos");
        assertEquals(2, lista.size(), lista.toString());
        assertEquals(antes, lista.get(0).get("agendamentoId").asInt());
        assertEquals(depois, lista.get(1).get("agendamentoId").asInt());

        JsonNode item = lista.get(0);
        assertEquals(7, item.size(), "só os campos do AgendamentoDoCliente: " + item);
        assertEquals(manicureId, item.get("servicoId").asInt());
        assertEquals("Manicure simples", item.get("servicoNome").asText());
        assertEquals("Ana Beatriz Costa", item.get("profissionalNome").asText());
        assertEquals("ana-unhas", item.get("profissionalSlug").asText());
        assertEquals(semanaQueVem, LocalDateTime.parse(item.get("inicio").asText()));
        assertEquals(semanaQueVem.plusMinutes(45), LocalDateTime.parse(item.get("fim").asText()));
        assertEquals("bia-cabelos", lista.get(1).get("profissionalSlug").asText());
        assertEquals("Escova", lista.get(1).get("servicoNome").asText());
    }

    @Test
    void clienteSemAgendamentosFuturosRecebeListaVazia() throws Exception {
        agendar(anaId, manicureId, joanaId, semanaQueVem, 45);

        HttpResponse<String> r = listar(MARIA);

        assertEquals(200, r.statusCode(), r.body());
        assertTrue(json(r).get("agendamentos").isArray());
        assertEquals(0, json(r).get("agendamentos").size());
    }

    @Test
    void telefoneFixoComOitoDigitosEhAceito() throws Exception {
        try (Connection c = banco.conectar()) {
            new ClienteRepositorio().inserir(c, "Dona Cida", "+551932221111");
        }
        assertEquals(200, listar("+551932221111").statusCode());
    }

    // ---- TELEFONE_INVALIDO ----

    @ParameterizedTest
    @ValueSource(strings = {"19988887777", "5519988887777", "+55199888877", "+551998888777766", "+5501988887777",
            "+1 415 555 0100", "+55 19 98888-7777", ""})
    void telefoneForaDoFormatoDaTelefoneInvalido(String telefone) throws Exception {
        assertErro(400, "TELEFONE_INVALIDO", listar(telefone));
    }

    @Test
    void semParametroTelefoneDaTelefoneInvalido() throws Exception {
        assertErro(400, "TELEFONE_INVALIDO", get("/agendamentos/do-cliente"));
    }

    // ---- CLIENTE_NAO_ENCONTRADO ----

    @Test
    void telefoneNaoCadastradoDaClienteNaoEncontrado() throws Exception {
        assertErro(404, "CLIENTE_NAO_ENCONTRADO", listar("+5511900001111"));
    }
}
