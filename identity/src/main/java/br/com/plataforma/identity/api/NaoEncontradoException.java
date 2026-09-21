package br.com.plataforma.identity.api;

/** Registro inexistente ou de outro tenant — os dois casos respondem 404 (Contrato §8.4). */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
