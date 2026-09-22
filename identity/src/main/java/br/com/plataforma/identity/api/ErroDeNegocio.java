package br.com.plataforma.identity.api;

import org.springframework.http.HttpStatus;

/**
 * Recusa por regra, com o código que vai em errors[].codigo (contratos/identity.yaml): 400 para
 * dado que não passa na validação de conteúdo, 409 para duplicidade e 422 para regra de negócio
 * (Contrato §8.4).
 */
public class ErroDeNegocio extends RuntimeException {

    private final HttpStatus status;
    private final String campo;
    private final String codigo;

    private ErroDeNegocio(HttpStatus status, String campo, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.campo = campo;
        this.codigo = codigo;
    }

    public static ErroDeNegocio invalido(String campo, String codigo, String mensagem) {
        return new ErroDeNegocio(HttpStatus.BAD_REQUEST, campo, codigo, mensagem);
    }

    public static ErroDeNegocio conflito(String campo, String codigo, String mensagem) {
        return new ErroDeNegocio(HttpStatus.CONFLICT, campo, codigo, mensagem);
    }

    public static ErroDeNegocio regra(String campo, String codigo, String mensagem) {
        return new ErroDeNegocio(HttpStatus.UNPROCESSABLE_ENTITY, campo, codigo, mensagem);
    }

    public HttpStatus status() {
        return status;
    }

    public ErroCampo comoErroDeCampo() {
        return new ErroCampo(campo, codigo, getMessage());
    }
}
