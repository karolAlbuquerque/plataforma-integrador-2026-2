package br.com.plataforma.identity.api;

/** Limite de falhas de login atingido, por e-mail ou por IP. Responde 429. */
public class MuitasTentativasException extends RuntimeException {

    public MuitasTentativasException(String mensagem) {
        super(mensagem);
    }
}
