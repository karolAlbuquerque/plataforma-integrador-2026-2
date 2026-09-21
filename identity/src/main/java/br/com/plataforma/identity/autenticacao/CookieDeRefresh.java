package br.com.plataforma.identity.autenticacao;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token (decisão D13): HttpOnly, nenhum JavaScript o lê; SameSite=Strict, o
 * navegador não o envia a partir de outro site; Path restrito às rotas de sessão.
 */
@Component
public class CookieDeRefresh {

    public static final String NOME = "refresh_token";
    static final String CAMINHO = "/api/identity/auth";

    private final boolean seguro;

    public CookieDeRefresh(@Value("${identity.cookie.seguro}") boolean seguro) {
        this.seguro = seguro;
    }

    /**
     * Vive até o fim da sessão, não além: a rotação troca o valor, mas mantém o prazo. Arredonda
     * para cima — quem decide se a sessão acabou é o expira_em gravado no banco, não o cookie.
     */
    public ResponseCookie criar(String valor, Instant fimDaSessao) {
        long segundos = Math.max(0, Duration.between(Instant.now(), fimDaSessao).plusMillis(999).toSeconds());
        return base(valor).maxAge(segundos).build();
    }

    public ResponseCookie apagar() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(seguro)
                .sameSite("Strict")
                .path(CAMINHO);
    }
}
