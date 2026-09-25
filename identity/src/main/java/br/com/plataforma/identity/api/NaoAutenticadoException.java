package br.com.plataforma.identity.api;

/** Credencial recusada. A mensagem é a mesma para todos os motivos, para não revelar qual foi. */
public class NaoAutenticadoException extends RuntimeException {

    private final ErroCampo erro;

    public NaoAutenticadoException(String mensagem) {
        this(mensagem, null);
    }

    /** Com o motivo em errors[0], quando a tela precisa agir diferente conforme ele (voltar à senha). */
    public NaoAutenticadoException(String mensagem, ErroCampo erro) {
        super(mensagem);
        this.erro = erro;
    }

    public ErroCampo erro() {
        return erro;
    }
}
