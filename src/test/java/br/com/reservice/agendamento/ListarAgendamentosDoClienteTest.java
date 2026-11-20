package br.com.reservice.agendamento;

import br.com.reservice.TesteIntegracao;
import br.com.reservice.banco.Banco;
import br.com.reservice.servico.ServicoRepositorio;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CT8 listarAgendamentosDoCliente.
 *
 * Na H05 o cliente é identificado pelo JWT.
 */
class ListarAgendamentosDoClienteTest extends TesteIntegracao {

    private static final String MARIA =
            "+5519988887777";

    private static final String JOANA =
            "+5519911112222";

    private static final String SENHA =
            "segredo123";

    private final AgendamentoRepositorio agendamentos =
            new AgendamentoRepositorio();

    private int anaId;
    private int biaId;

    private int manicureId;
    private int escovaDaBiaId;

    private int mariaId;
    private int joanaId;

    private String tokenMaria;
    private String tokenJoana;

    /**
     * Uma semana à frente para garantir
     * agendamentos futuros.
     */
    private LocalDateTime semanaQueVem;

    @BeforeEach
    void preparar() throws Exception {

        anaId = criarProfissional(
                "Ana Beatriz Costa",
                "ana@exemplo.com",
                "x",
                "+5519999990000",
                "ana-unhas");

        biaId = criarProfissional(
                "Bia Lima",
                "bia@exemplo.com",
                "x",
                "+5519999991111",
                "bia-cabelos");

        ServicoRepositorio servicos =
                new ServicoRepositorio(banco);

        manicureId =
                servicos.inserir(
                        anaId,
                        "Manicure simples",
                        45,
                        new BigDecimal("35.00"))
                        .id();

        escovaDaBiaId =
                servicos.inserir(
                        biaId,
                        "Escova",
                        30,
                        new BigDecimal("50.00"))
                        .id();

        ClienteLogado maria =
                criarClienteLogado(
                        "Maria Souza",
                        MARIA,
                        SENHA);

        ClienteLogado joana =
                criarClienteLogado(
                        "Joana Lima",
                        JOANA,
                        SENHA);

        mariaId = maria.id();
        tokenMaria = maria.token();

        joanaId = joana.id();
        tokenJoana = joana.token();

        semanaQueVem =
                Banco.agora()
                        .plusDays(7)
                        .withHour(10)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
    }

    /**
     * Grava direto no banco para permitir
     * criar também agendamentos passados.
     */
    private int agendar(
            int profissionalId,
            int servicoId,
            int clienteId,
            LocalDateTime inicio,
            int minutos) throws Exception {

        try (Connection c = banco.conectar()) {

            return agendamentos
                    .inserir(
                            c,
                            profissionalId,
                            servicoId,
                            clienteId,
                            inicio,
                            inicio.plusMinutes(minutos))
                    .id();
        }
    }

    private void cancelarNoBanco(
            int agendamentoId) throws Exception {

        try (Connection c = banco.conectar();
             PreparedStatement ps =
                     c.prepareStatement(
                             "UPDATE agendamento "
                                     + "SET status = 'cancelado' "
                                     + "WHERE id = ?")) {

            ps.setInt(
                    1,
                    agendamentoId);

            ps.executeUpdate();
        }
    }

    private HttpResponse<String> listar(
            String token) throws Exception {

        return get(
                "/agendamentos/do-cliente",
                token);
    }

    @Test
    void devolveSoOsFuturosConfirmadosDoClienteEmOrdemDeInicio()
            throws Exception {

        /*
         * Gravamos fora de ordem para provar
         * que a consulta ordena pelo início.
         */
        int depois =
                agendar(
                        biaId,
                        escovaDaBiaId,
                        mariaId,
                        semanaQueVem.plusDays(1),
                        30);

        int antes =
                agendar(
                        anaId,
                        manicureId,
                        mariaId,
                        semanaQueVem,
                        45);

        /*
         * Não devem aparecer.
         */

        agendar(
                anaId,
                manicureId,
                mariaId,
                Banco.agora().minusDays(1),
                45);

        agendar(
                anaId,
                manicureId,
                mariaId,
                Banco.agora().minusMinutes(10),
                45);

        cancelarNoBanco(
                agendar(
                        anaId,
                        manicureId,
                        mariaId,
                        semanaQueVem.plusHours(2),
                        45));

        agendar(
                anaId,
                manicureId,
                joanaId,
                semanaQueVem.plusHours(4),
                45);

        HttpResponse<String> r =
                listar(tokenMaria);

        assertEquals(
                200,
                r.statusCode(),
                r.body());

        JsonNode corpo =
                json(r);

        assertEquals(
                1,
                corpo.size());

        JsonNode lista =
                corpo.get("agendamentos");

        assertTrue(
                lista.isArray());

        assertEquals(
                2,
                lista.size());

        assertEquals(
                antes,
                lista.get(0)
                        .get("agendamentoId")
                        .asInt());

        assertEquals(
                depois,
                lista.get(1)
                        .get("agendamentoId")
                        .asInt());

        JsonNode primeiro =
                lista.get(0);

        /*
         * Estrutura atual da H05:
         * agendamentoId
         * servicoNome
         * profissionalNome
         * inicio
         * fim
         * status
         */
        assertEquals(
                6,
                primeiro.size());

        assertEquals(
                "Manicure simples",
                primeiro.get("servicoNome")
                        .asText());

        assertEquals(
                "Ana Beatriz Costa",
                primeiro.get("profissionalNome")
                        .asText());

        assertEquals(
                semanaQueVem,
                LocalDateTime.parse(
                        primeiro.get("inicio")
                                .asText()));

        assertEquals(
                semanaQueVem.plusMinutes(45),
                LocalDateTime.parse(
                        primeiro.get("fim")
                                .asText()));

        assertEquals(
                "confirmado",
                primeiro.get("status")
                        .asText());

        JsonNode segundo =
                lista.get(1);

        assertEquals(
                "Escova",
                segundo.get("servicoNome")
                        .asText());

        assertEquals(
                "Bia Lima",
                segundo.get("profissionalNome")
                        .asText());
    }

    @Test
    void clienteSemAgendamentosFuturosRecebeListaVazia()
            throws Exception {

        agendar(
                anaId,
                manicureId,
                joanaId,
                semanaQueVem,
                45);

        HttpResponse<String> r =
                listar(tokenMaria);

        assertEquals(
                200,
                r.statusCode(),
                r.body());

        JsonNode lista =
                json(r)
                        .get("agendamentos");

        assertTrue(
                lista.isArray());

        assertEquals(
                0,
                lista.size());
    }

    @Test
    void clienteVeSomenteOsPropriosAgendamentos()
            throws Exception {

        int daMaria =
                agendar(
                        anaId,
                        manicureId,
                        mariaId,
                        semanaQueVem,
                        45);

        int daJoana =
                agendar(
                        biaId,
                        escovaDaBiaId,
                        joanaId,
                        semanaQueVem.plusHours(2),
                        30);

        JsonNode listaMaria =
                json(listar(tokenMaria))
                        .get("agendamentos");

        JsonNode listaJoana =
                json(listar(tokenJoana))
                        .get("agendamentos");

        assertEquals(
                1,
                listaMaria.size());

        assertEquals(
                daMaria,
                listaMaria.get(0)
                        .get("agendamentoId")
                        .asInt());

        assertEquals(
                1,
                listaJoana.size());

        assertEquals(
                daJoana,
                listaJoana.get(0)
                        .get("agendamentoId")
                        .asInt());
    }

    // ---------- AUTENTICAÇÃO ----------

    @Test
    void semTokenDaNaoAutenticado()
            throws Exception {

        assertErro(
                401,
                "NAO_AUTENTICADO",
                listar(null));
    }

    @Test
    void tokenInvalidoDaNaoAutenticado()
            throws Exception {

        assertErro(
                401,
                "NAO_AUTENTICADO",
                listar("token-invalido"));
    }
}