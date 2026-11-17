package br.com.reservice.horario;

import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.banco.Banco;
import br.com.reservice.http.ErroApi;
import br.com.reservice.http.Requisicao;
import br.com.reservice.http.Resposta;
import br.com.reservice.profissional.Profissional;
import br.com.reservice.profissional.ProfissionalRepositorio;
import br.com.reservice.servico.Servico;
import br.com.reservice.servico.ServicoRepositorio;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CT5 listarHorariosLivres ({@code GET /profissionais/{slug}/horarios-livres?servicoId=&data=}), sem login.
 *
 * <p>Dentro de cada faixa da grade do dia, oferece um início a cada 30 minutos, a partir do começo da faixa,
 * desde que o serviço caiba inteiro na faixa e não se sobreponha a agendamento confirmado. Horário que apenas
 * encosta no agendamento é livre. Hoje, só aparecem inícios depois de agora, o mesmo critério do CT7.
 */
public class HorarioLivreRecurso {

    static final int PASSO_MINUTOS = 30;
    private static final Pattern DATA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern NUMERO = Pattern.compile("^\\d{1,9}$");

    /** Intervalo ocupado por um agendamento confirmado. */
    record Periodo(LocalDateTime inicio, LocalDateTime fim) {
    }

    /** Datas e horas como texto ISO sem fuso, com segundos (ex.: 2026-10-20T09:00:00), como no CT5. */
    public record HorariosLivres(String data, int servicoId, List<String> horariosLivres) {
    }

    private final Banco banco;
    private final ProfissionalRepositorio profissionais;
    private final ServicoRepositorio servicos;
    private final HorarioRepositorio horarios;
    private final AgendamentoRepositorio agendamentos;

    public HorarioLivreRecurso(Banco banco, ProfissionalRepositorio profissionais, ServicoRepositorio servicos,
                               HorarioRepositorio horarios, AgendamentoRepositorio agendamentos) {
        this.banco = banco;
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.horarios = horarios;
        this.agendamentos = agendamentos;
    }

    public Resposta listarHorariosLivres(Requisicao requisicao) throws SQLException {
        Profissional profissional = profissionais.buscarPorSlug(requisicao.parametro(0))
                .orElseThrow(() -> new ErroApi(404, "PROFISSIONAL_NAO_ENCONTRADO",
                        "Não encontramos esse profissional."));
        int servicoId = lerServicoId(requisicao.parametroConsulta("servicoId").orElse(null));
        LocalDateTime agora = Banco.agora();
        LocalDate data = lerData(requisicao.parametroConsulta("data").orElse(null), agora.toLocalDate());

        try (Connection c = banco.conectar()) {
            Servico servico = servicos.buscarPorId(c, servicoId)
                    .filter(s -> s.profissionalId() == profissional.id())
                    .orElseThrow(() -> new ErroApi(404, "SERVICO_NAO_ENCONTRADO", "Não encontramos esse serviço."));
            List<HorarioAtendimento> faixas = horarios.listarDoDia(c, profissional.id(), data.getDayOfWeek());
            // Todo agendamento começa e termina no mesmo dia (CT7), então "começa no dia" cobre o dia inteiro.
            List<Periodo> ocupados = agendamentos.listarConfirmadosDoDia(c, profissional.id(), data).stream()
                    .map(a -> new Periodo(a.inicio(), a.fim()))
                    .toList();

            List<String> livres = calcular(faixas, ocupados, data, servico.duracaoMinutos(), agora).stream()
                    .map(DateTimeFormatter.ISO_LOCAL_DATE_TIME::format)
                    .toList();
            return Resposta.ok(new HorariosLivres(data.toString(), servico.id(), livres));
        }
    }

    /** Início de cada horário livre do dia, em ordem. Sem acesso a banco, para ser testado direto. */
    static List<LocalDateTime> calcular(List<HorarioAtendimento> faixas, List<Periodo> ocupados, LocalDate data,
                                        int duracaoMinutos, LocalDateTime agora) {
        List<LocalDateTime> livres = new ArrayList<>();
        for (HorarioAtendimento faixa : faixas) {
            LocalDateTime limite = data.atTime(LocalTime.parse(faixa.horaFim()));
            LocalDateTime inicio = data.atTime(LocalTime.parse(faixa.horaInicio()));
            while (!inicio.plusMinutes(duracaoMinutos).isAfter(limite)) {
                LocalDateTime fim = inicio.plusMinutes(duracaoMinutos);
                if (inicio.isAfter(agora) && !conflita(inicio, fim, ocupados)) {
                    livres.add(inicio);
                }
                inicio = inicio.plusMinutes(PASSO_MINUTOS);
            }
        }
        return livres;
    }

    private static boolean conflita(LocalDateTime inicio, LocalDateTime fim, List<Periodo> ocupados) {
        for (Periodo p : ocupados) {
            if (inicio.isBefore(p.fim()) && p.inicio().isBefore(fim)) {
                return true;
            }
        }
        return false;
    }

    private static int lerServicoId(String valor) {
        if (valor == null || !NUMERO.matcher(valor).matches()) {
            throw ErroApi.dadosInvalidos("Escolha o serviço.");
        }
        return Integer.parseInt(valor);
    }

    private static LocalDate lerData(String valor, LocalDate hoje) {
        ErroApi invalida = new ErroApi(400, "DATA_INVALIDA", "Escolha uma data válida, de hoje em diante.");
        if (valor == null || !DATA.matcher(valor).matches()) {
            throw invalida;
        }
        LocalDate data;
        try {
            data = LocalDate.parse(valor);
        } catch (DateTimeException e) {
            throw invalida;
        }
        if (data.isBefore(hoje)) {
            throw invalida;
        }
        return data;
    }
}
