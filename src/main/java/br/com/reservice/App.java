package br.com.reservice;

import br.com.reservice.agendamento.AgendamentoRecurso;
import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.agendamento.LembreteSocketRecurso;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRecurso;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.cliente.RecuperacaoSenhaRecurso;
import br.com.reservice.cliente.RecuperacaoSenhaRepositorio;
import br.com.reservice.cliente.RecuperacaoSenhaSocketRecurso;
import br.com.reservice.horario.HorarioLivreRecurso;
import br.com.reservice.horario.HorarioRecurso;
import br.com.reservice.horario.HorarioRepositorio;
import br.com.reservice.http.Servidor;
import br.com.reservice.profissional.ProfissionalRepositorio;
import br.com.reservice.servico.ServicoRecurso;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.sessao.SessaoRecurso;
import br.com.reservice.sessao.Tokens;
import br.com.reservice.socket.ServidorSocket;

import java.time.Clock;
import java.util.logging.Logger;

/** Ponto de entrada do Backend 1. */
public final class App {

    private static final Logger LOG =
            Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.doAmbiente();

        Servidor servidor =
                montar(config, Clock.systemUTC());

        servidor.iniciar(
                config.porta(),
                config.threads());

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(servidor::parar));

        LOG.info(
                "Backend 1 ouvindo na porta "
                        + servidor.porta());

        ServidorSocket socket =
                montarSocket(config);

        socket.iniciar(
                config.portaSocket(),
                config.threads());

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(socket::parar));

        LOG.info(
                "Socket TCP do Backend 1 ouvindo na porta "
                        + socket.porta());
    }

    public static ServidorSocket montarSocket(
            Config config) {

        Banco banco = new Banco(
                config.bancoUrl(),
                config.bancoUsuario(),
                config.bancoSenha());

        RecuperacaoSenhaRepositorio recuperacoes =
                new RecuperacaoSenhaRepositorio();

        RecuperacaoSenhaSocketRecurso recurso =
                new RecuperacaoSenhaSocketRecurso(
                        banco,
                        recuperacoes);

        LembreteSocketRecurso lembretes =
                new LembreteSocketRecurso(
                        banco,
                        new AgendamentoRepositorio());

        return new ServidorSocket(
                config.chaveInterna())

                .operacao(
                        "listarLembretesPendentes",
                        lembretes::listarLembretesPendentes)

                .operacao(
                        "registrarLembrete",
                        lembretes::registrarLembrete)

                .operacao(
                        "buscar_recuperacao_senha",
                        recurso::buscarPendente)

                .operacao(
                        "registrar_envio_recuperacao",
                        recurso::registrarEnvio);
    }

    public static Servidor montar(
            Config config,
            Clock relogio) {

        Banco banco = new Banco(
                config.bancoUrl(),
                config.bancoUsuario(),
                config.bancoSenha());

        Tokens tokens = new Tokens(
                config.jwtSegredo(),
                config.jwtValidade(),
                relogio);

        ProfissionalRepositorio profissionais =
                new ProfissionalRepositorio(banco);

        ServicoRepositorio servicos =
                new ServicoRepositorio(banco);

        ClienteRepositorio clientes =
                new ClienteRepositorio();

        HorarioRepositorio horarios =
                new HorarioRepositorio();

        AgendamentoRepositorio agendamentos =
                new AgendamentoRepositorio();

        RecuperacaoSenhaRepositorio recuperacoes =
                new RecuperacaoSenhaRepositorio();

        SessaoRecurso sessoes =
                new SessaoRecurso(
                        profissionais,
                        tokens);

        ServicoRecurso servicoRecurso =
                new ServicoRecurso(
                        servicos,
                        profissionais,
                        tokens);

        ClienteRecurso clienteRecurso =
                new ClienteRecurso(
                        banco,
                        clientes,
                        tokens);

        AgendamentoRecurso agendamentoRecurso =
                new AgendamentoRecurso(
                        banco,
                        servicos,
                        horarios,
                        clientes,
                        agendamentos,
                        tokens);

        HorarioRecurso horarioRecurso =
                new HorarioRecurso(
                        banco,
                        horarios,
                        agendamentos,
                        tokens);

        HorarioLivreRecurso horarioLivreRecurso =
                new HorarioLivreRecurso(
                        banco,
                        profissionais,
                        servicos,
                        horarios,
                        agendamentos);

        RecuperacaoSenhaRecurso recuperacaoSenhaRecurso =
                new RecuperacaoSenhaRecurso(
                        banco,
                        clientes,
                        recuperacoes);

        return new Servidor(
                config.corsOrigens())

                .rota(
                        "POST",
                        "/sessoes",
                        sessoes::abrirSessao)

                .rota(
                        "POST",
                        "/servicos",
                        servicoRecurso::cadastrarServico)

                .rota(
                        "PUT",
                        "/horarios",
                        horarioRecurso::definirHorarios)

                .rota(
                        "GET",
                        "/profissionais/([^/]+)/servicos",
                        servicoRecurso::listarServicos)

                .rota(
                        "GET",
                        "/profissionais/([^/]+)/horarios-livres",
                        horarioLivreRecurso::listarHorariosLivres)

                .rota(
                        "POST",
                        "/agendamentos",
                        agendamentoRecurso::criarAgendamento)

                .rota(
                        "GET",
                        "/agendamentos",
                        agendamentoRecurso::listarAgendamentosDoDia)

                .rota(
                        "GET",
                        "/agendamentos/do-cliente",
                        agendamentoRecurso::listarDoCliente)

                .rota(
                        "PATCH",
                        "/agendamentos/([^/]+)/cancelamento",
                        agendamentoRecurso::cancelarAgendamento)

                .rota(
                        "PATCH",
                        "/agendamentos/([^/]+)/remarcacao",
                        agendamentoRecurso::remarcarAgendamento)

                .rota(
                        "POST",
                        "/clientes",
                        clienteRecurso::cadastrar)

                .rota(
                        "POST",
                        "/clientes/sessoes",
                        clienteRecurso::entrar)

                .rota(
                        "POST",
                        "/clientes/recuperacao-senha",
                        recuperacaoSenhaRecurso::solicitar)

                .rota(
                        "POST",
                        "/clientes/recuperacao-senha/confirmar",
                        recuperacaoSenhaRecurso::confirmar);
    }
}