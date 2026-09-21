package br.com.plataforma.identity.api;

import java.util.List;

/** Envelope de toda resposta HTTP, inclusive as de erro (Contrato §8.2). O JWKS é a única exceção. */
public record Resposta<T>(boolean success, T data, String message, List<ErroCampo> errors) {

    public static <T> Resposta<T> ok(T data) {
        return new Resposta<>(true, data, null, List.of());
    }

    public static <T> Resposta<T> falha(String mensagem) {
        return new Resposta<>(false, null, mensagem, List.of());
    }

    public static <T> Resposta<T> falha(String mensagem, List<ErroCampo> erros) {
        return new Resposta<>(false, null, mensagem, erros);
    }
}
