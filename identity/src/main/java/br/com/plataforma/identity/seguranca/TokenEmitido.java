package br.com.plataforma.identity.seguranca;

import java.time.Instant;

/** Access token assinado e quando ele vence. */
public record TokenEmitido(String valor, long validadeEmSegundos, Instant expiraEm) {
}
