package br.com.reservice.http;

/**
 * Erro que vira resposta HTTP no formato combinado na visão geral:
 * {@code { "erro": CODIGO, "mensagem": texto para a tela }}.
 */
public class ErroApi extends RuntimeException {

    private final int status;
    private final String codigo;

    public ErroApi(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public static ErroApi dadosInvalidos(String mensagem) {
        return new ErroApi(400, "DADOS_INVALIDOS", mensagem);
    }

    public static ErroApi naoAutenticado() {
        return new ErroApi(401, "NAO_AUTENTICADO", "Sua sessão expirou. Entre de novo para continuar.");
    }
}
