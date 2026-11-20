package br.com.reservice.horario;

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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CT5 listarHorariosLivres. As datas são calculadas a partir de hoje, nunca fixas. */
class ListarHorariosLivresTest extends TesteIntegracao {

    private final AtomicInteger telefones = new AtomicInteger();
    private int anaId;
    private int servicoId;
    private LocalDate segunda;

    @BeforeEach
    void prepararAna() throws Exception {
        anaId = criarProfissional("Ana", "ana@exemplo.com", "segredo123", "+5519999990000", "ana-unhas");
        servicoId = new ServicoRepositorio(banco).inserir(anaId, "Manicure", 60, new BigDecimal("50.00")).id();
        segunda = proximoDia(DayOfWeek.MONDAY);
        inserirFaixa(anaId, "segunda", "09:00", "12:00");
    }

    private static LocalDate proximoDia(DayOfWeek dia) {
        LocalDate d = Banco.agora().toLocalDate().plusDays(1);
        while (d.getDayOfWeek() != dia) {
            d = d.plusDays(1);
        }
        return d;
    }

    private HttpResponse<String> consultar(String slug, String servico, String data) throws Exception {
        StringBuilder q = new StringBuilder();
        if (servico != null) {
            q.append("servicoId=").append(servico);
        }
        if (data != null) {
            q.append(q.length() > 0 ? "&" : "").append("data=").append(data);
        }
        return get("/profissionais/" + slug + "/horarios-livres" + (q.length() > 0 ? "?" + q : ""));
    }

    private List<String> livres(HttpResponse<String> r) throws Exception {
        assertEquals(200, r.statusCode(), r.body());
        List<String> lista = new ArrayList<>();
        json(r).get("horariosLivres").forEach(n -> lista.add(n.asText()));
        return lista;
    }

    private void inserirFaixa(int profissionalId, String dia, String inicio, String fim) throws Exception {
        String sql = "INSERT INTO horario_atendimento (profissional_id, dia_semana, hora_inicio, hora_fim) VALUES (?, ?, ?, ?)";
        try (Connection c = banco.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, profissionalId);
            ps.setString(2, dia);
            ps.setString(3, inicio);
            ps.setString(4, fim);
            ps.executeUpdate();
        }
    }

    private void inserirAgendamento(
        int profissionalId,
        int servico,
        LocalDateTime inicio,
        LocalDateTime fim,
        String status) throws Exception {

    try (Connection c = banco.conectar()) {

        int clienteId;

        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO cliente
                    (nome, telefone, senha_hash, criado_em)
                VALUES (?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, "Cliente");
            ps.setString(
                    2,
                    "+55199000"
                            + String.format(
                                    "%05d",
                                    telefones.incrementAndGet()));
            ps.setString(3, "hash-de-teste");
            ps.setObject(4, Banco.agora());

            ps.executeUpdate();

            try (ResultSet chaves =
                         ps.getGeneratedKeys()) {

                chaves.next();
                clienteId =
                        chaves.getInt(1);
            }
        }

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
                        criado_em,
                        atualizado_em
                    )
                VALUES (?, ?, ?, ?, ?, ?, 'pendente', ?, ?)
                """;

        try (PreparedStatement ps =
                     c.prepareStatement(sql)) {

            ps.setInt(1, profissionalId);
            ps.setInt(2, servico);
            ps.setInt(3, clienteId);
            ps.setObject(4, inicio);
            ps.setObject(5, fim);
            ps.setString(6, status);
            ps.setObject(7, Banco.agora());
            ps.setObject(8, Banco.agora());

            ps.executeUpdate();
        }
    }
}

    @Test
    void listaOsHorariosNoFormatoDoContrato() throws Exception {
        HttpResponse<String> r = consultar("ana-unhas", String.valueOf(servicoId), segunda.toString());

        assertEquals(200, r.statusCode(), r.body());
        JsonNode corpo = json(r);
        assertEquals(segunda.toString(), corpo.get("data").asText());
        assertEquals(servicoId, corpo.get("servicoId").asInt());
        assertEquals(List.of(segunda + "T09:00:00", segunda + "T09:30:00", segunda + "T10:00:00",
                segunda + "T10:30:00", segunda + "T11:00:00"), livres(r));
    }

    @Test
    void diaSemGradeDevolveListaVazia() throws Exception {
        HttpResponse<String> r = consultar("ana-unhas", String.valueOf(servicoId), proximoDia(DayOfWeek.TUESDAY).toString());

        assertEquals(List.of(), livres(r));
    }

    @Test
    void agendamentoConfirmadoBloqueiaOsHorariosQueSobrepoe() throws Exception {
        inserirAgendamento(anaId, servicoId, segunda.atTime(10, 0), segunda.atTime(11, 0), "confirmado");

        List<String> livres = livres(consultar("ana-unhas", String.valueOf(servicoId), segunda.toString()));

        assertEquals(List.of(segunda + "T09:00:00", segunda + "T11:00:00"), livres);
    }

    @Test
    void agendamentoCanceladoNaoBloqueia() throws Exception {
        inserirAgendamento(anaId, servicoId, segunda.atTime(10, 0), segunda.atTime(11, 0), "cancelado");

        assertEquals(5, livres(consultar("ana-unhas", String.valueOf(servicoId), segunda.toString())).size());
    }

    @Test
    void agendamentoDeOutroProfissionalNaoBloqueia() throws Exception {
        int brunoId = criarProfissional("Bruno", "bruno@exemplo.com", "segredo123", "+5519988880000", "bruno-barba");
        int servicoBruno = new ServicoRepositorio(banco).inserir(brunoId, "Barba", 60, new BigDecimal("40.00")).id();
        inserirAgendamento(brunoId, servicoBruno, segunda.atTime(10, 0), segunda.atTime(11, 0), "confirmado");

        assertEquals(5, livres(consultar("ana-unhas", String.valueOf(servicoId), segunda.toString())).size());
    }

    @Test
    void hojeSoOfereceInicioDepoisDeAgora() throws Exception {
        LocalDate hoje = Banco.agora().toLocalDate();
        String dia = switch (hoje.getDayOfWeek()) {
            case SUNDAY -> "domingo";
            case MONDAY -> "segunda";
            case TUESDAY -> "terca";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sabado";
        };
        inserirFaixa(anaId, dia, "00:00", "23:30");
        LocalDateTime antes = Banco.agora();

        HttpResponse<String> r = consultar("ana-unhas", String.valueOf(servicoId), hoje.toString());

        assertEquals(200, r.statusCode(), r.body());
        for (String inicio : livres(r)) {
            assertTrue(LocalDateTime.parse(inicio).isAfter(antes), "horário que já passou: " + inicio);
        }
    }

    @Test
    void slugInexistenteEhNaoEncontrado() throws Exception {
        assertErro(404, "PROFISSIONAL_NAO_ENCONTRADO", consultar("ninguem", String.valueOf(servicoId), segunda.toString()));
    }

    @Test
    void servicoInexistenteEhNaoEncontrado() throws Exception {
        assertErro(404, "SERVICO_NAO_ENCONTRADO", consultar("ana-unhas", "999999", segunda.toString()));
    }

    @Test
    void servicoDeOutroProfissionalEhNaoEncontrado() throws Exception {
        int brunoId = criarProfissional("Bruno", "bruno@exemplo.com", "segredo123", "+5519988880000", "bruno-barba");
        int servicoBruno = new ServicoRepositorio(banco).inserir(brunoId, "Barba", 60, new BigDecimal("40.00")).id();

        assertErro(404, "SERVICO_NAO_ENCONTRADO", consultar("ana-unhas", String.valueOf(servicoBruno), segunda.toString()));
    }

    @Test
    void dataForaDoFormatoOuInexistenteEhInvalida() throws Exception {
        String s = String.valueOf(servicoId);
        assertErro(400, "DATA_INVALIDA", consultar("ana-unhas", s, "20-10-2026"));
        assertErro(400, "DATA_INVALIDA", consultar("ana-unhas", s, "2026-02-30"));
        assertErro(400, "DATA_INVALIDA", consultar("ana-unhas", s, "amanha"));
        assertErro(400, "DATA_INVALIDA", consultar("ana-unhas", s, null));
    }

    @Test
    void dataAnteriorAHojeEhInvalida() throws Exception {
        String ontem = Banco.agora().toLocalDate().minusDays(1).toString();

        assertErro(400, "DATA_INVALIDA", consultar("ana-unhas", String.valueOf(servicoId), ontem));
    }

    @Test
    void hojeEhUmaDataValida() throws Exception {
        HttpResponse<String> r = consultar("ana-unhas", String.valueOf(servicoId), Banco.agora().toLocalDate().toString());

        assertEquals(200, r.statusCode(), r.body());
        assertFalse(json(r).get("horariosLivres").isNull());
    }

    @Test
    void servicoIdAusenteOuNaoNumericoEhDadosInvalidos() throws Exception {
        assertErro(400, "DADOS_INVALIDOS", consultar("ana-unhas", null, segunda.toString()));
        assertErro(400, "DADOS_INVALIDOS", consultar("ana-unhas", "abc", segunda.toString()));
    }
}
