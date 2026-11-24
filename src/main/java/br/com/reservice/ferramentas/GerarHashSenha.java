package br.com.reservice.ferramentas;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Gera o {@code senha_hash} (bcrypt) para cadastrar um profissional direto no banco,
 * enquanto não há tela de cadastro. Uso: {@code java -cp backend1.jar br.com.reservice.ferramentas.GerarHashSenha <senha>}.
 */
public final class GerarHashSenha {

    private GerarHashSenha() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: GerarHashSenha <senha>");
            System.exit(1);
        }
        System.out.println(BCrypt.hashpw(args[0], BCrypt.gensalt(10)));
    }
}
