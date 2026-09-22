package br.com.plataforma.identity.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token aleatório que só o dono conhece: refresh token, convite e recuperação de senha. O valor
 * vai para o cookie ou para o e-mail; o banco guarda só o SHA-256 — quem ler a tabela não consegue
 * se passar por ninguém.
 */
public final class TokenOpaco {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenOpaco() {
    }

    /** 256 bits aleatórios, em base64 sem padding — seguro para cookie e para URL. */
    public static String gerar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
