package br.com.plataforma.identity.segundofator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Cifra o segredo TOTP antes de gravar. Diferente da senha, ele não pode virar hash: o identity
 * precisa dele para calcular o código. Com a cifra, uma cópia do banco sem a chave não gera códigos.
 *
 * A chave é SEGUNDO_FATOR_CHAVE (base64 de 32 bytes), gerada pelo scripts/gerar-env.sh do infra.
 * Vazia só no perfil dev, que usa uma chave fixa de desenvolvimento — em qualquer outro perfil o
 * identity não sobe. Trocar a chave invalida todos os segundos fatores cadastrados.
 *
 * Formato gravado: base64(nonce de 12 bytes || texto cifrado com a tag GCM de 16 bytes).
 */
@Component
public class CifraDeSegredo {

    private static final Logger log = LoggerFactory.getLogger(CifraDeSegredo.class);
    private static final int NONCE = 12;
    private static final int TAG_EM_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKey chave;

    public CifraDeSegredo(@Value("${identity.segundo-fator.chave:}") String chaveEmBase64, Environment ambiente) {
        if (chaveEmBase64 == null || chaveEmBase64.isBlank()) {
            if (!ambiente.matchesProfiles("dev")) {
                throw new IllegalStateException(
                        "SEGUNDO_FATOR_CHAVE não definida. Gere com scripts/gerar-env.sh do infra-integrador-2026.");
            }
            log.warn("SEGUNDO_FATOR_CHAVE vazia: usando a chave fixa de desenvolvimento (só no perfil dev).");
            this.chave = new SecretKeySpec(sha256("chave-de-desenvolvimento-do-segundo-fator"), "AES");
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(chaveEmBase64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SEGUNDO_FATOR_CHAVE não é base64 válido.", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("SEGUNDO_FATOR_CHAVE precisa ter 32 bytes (AES-256); tem " + bytes.length + ".");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(byte[] segredo) {
        try {
            byte[] nonce = new byte[NONCE];
            ALEATORIO.nextBytes(nonce);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_EM_BITS, nonce));
            byte[] cifrado = cifra.doFinal(segredo);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(NONCE + cifrado.length).put(nonce).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o segredo do segundo fator.", e);
        }
    }

    /** @throws IllegalStateException se o texto foi cifrado com outra chave ou adulterado */
    public byte[] decifrar(String gravado) {
        try {
            byte[] tudo = Base64.getDecoder().decode(gravado);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_EM_BITS, tudo, 0, NONCE));
            return cifra.doFinal(tudo, NONCE, tudo.length - NONCE);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Segredo do segundo fator ilegível: a chave SEGUNDO_FATOR_CHAVE mudou?", e);
        }
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
