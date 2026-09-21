package br.com.plataforma.identity.api;

/** Credencial recusada. A mensagem é a mesma para todos os motivos, para não revelar qual foi. */
public class NaoAutenticadoException extends RuntimeException {

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
