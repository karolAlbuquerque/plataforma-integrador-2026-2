package br.com.plataforma.identity.mensageria;

/** Mensagem que nunca vai dar certo — campo faltando, usuário de outro tenant. Termina na .dlq. */
public class MensagemRecusadaException extends RuntimeException {

    public MensagemRecusadaException(String mensagem) {
        super(mensagem);
    }
}
