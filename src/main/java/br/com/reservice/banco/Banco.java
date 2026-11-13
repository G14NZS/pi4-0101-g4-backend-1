package br.com.reservice.banco;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Abre conexões JDBC com o MySQL (ADR 0001). Cada pedido abre e fecha a sua. */
public class Banco {

    /** Datas sem fuso, sempre no horário de Brasília (visão geral). */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /** Códigos de erro do MySQL usados para traduzir recusas do banco. */
    public static final int ERRO_VALOR_DUPLICADO = 1062;
    public static final int ERRO_CHAVE_ESTRANGEIRA = 1452;

    private final String url;
    private final String usuario;
    private final String senha;

    public Banco(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO).truncatedTo(ChronoUnit.SECONDS);
    }
}
