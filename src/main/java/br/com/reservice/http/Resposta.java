package br.com.reservice.http;

/** Resposta de sucesso: status HTTP e objeto a ser escrito como JSON. */
public record Resposta(int status, Object corpo) {

    public static Resposta ok(Object corpo) {
        return new Resposta(200, corpo);
    }

    public static Resposta criado(Object corpo) {
        return new Resposta(201, corpo);
    }
}
