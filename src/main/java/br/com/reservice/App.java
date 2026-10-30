package br.com.reservice;

import br.com.reservice.agendamento.AgendamentoRecurso;
import br.com.reservice.agendamento.AgendamentoRepositorio;
import br.com.reservice.banco.Banco;
import br.com.reservice.cliente.ClienteRepositorio;
import br.com.reservice.horario.HorarioRepositorio;
import br.com.reservice.http.Servidor;
import br.com.reservice.profissional.ProfissionalRepositorio;
import br.com.reservice.servico.ServicoRecurso;
import br.com.reservice.servico.ServicoRepositorio;
import br.com.reservice.sessao.SessaoRecurso;
import br.com.reservice.sessao.Tokens;

import java.time.Clock;
import java.util.logging.Logger;

/** Ponto de entrada do Backend 1. */
public final class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.doAmbiente();
        Servidor servidor = montar(config, Clock.systemUTC());
        servidor.iniciar(config.porta(), config.threads());
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
        LOG.info("Backend 1 ouvindo na porta " + servidor.porta());
    }

    /** Liga repositórios, recursos e rotas. O relógio é usado para emitir e conferir tokens. */
    public static Servidor montar(Config config, Clock relogio) {
        Banco banco = new Banco(config.bancoUrl(), config.bancoUsuario(), config.bancoSenha());
        Tokens tokens = new Tokens(config.jwtSegredo(), config.jwtValidade(), relogio);
        ProfissionalRepositorio profissionais = new ProfissionalRepositorio(banco);
        ServicoRepositorio servicos = new ServicoRepositorio(banco);

        SessaoRecurso sessoes = new SessaoRecurso(profissionais, tokens);
        ServicoRecurso servicoRecurso = new ServicoRecurso(servicos, profissionais, tokens);
        AgendamentoRecurso agendamentoRecurso = new AgendamentoRecurso(banco, servicos, new HorarioRepositorio(),
                new ClienteRepositorio(), new AgendamentoRepositorio());

        return new Servidor(config.corsOrigens())
                .rota("POST", "/sessoes", sessoes::abrirSessao)
                .rota("POST", "/servicos", servicoRecurso::cadastrarServico)
                .rota("GET", "/profissionais/([^/]+)/servicos", servicoRecurso::listarServicos)
                .rota("POST", "/agendamentos", agendamentoRecurso::criarAgendamento);
    }
}
