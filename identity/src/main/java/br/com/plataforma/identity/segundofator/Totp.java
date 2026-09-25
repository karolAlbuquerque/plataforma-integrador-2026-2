package br.com.plataforma.identity.segundofator;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Código de uso único baseado em tempo (RFC 6238), o mesmo dos aplicativos autenticadores:
 * HMAC-SHA1, seis dígitos, passos de 30 segundos. Escrito aqui, sem biblioteca, porque são poucas
 * linhas e os vetores da RFC o testam por inteiro.
 */
public final class Totp {

    static final int DIGITOS = 6;
    static final long PASSO_EM_SEGUNDOS = 30;
    /** Um passo antes e um depois: tolera relógio do celular até 30 s adiantado ou atrasado. */
    static final int TOLERANCIA = 1;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {
    }

    /** 160 bits, o tamanho recomendado pela RFC 4226 para HMAC-SHA1. */
    public static byte[] novoSegredo() {
        byte[] segredo = new byte[20];
        ALEATORIO.nextBytes(segredo);
        return segredo;
    }

    public static long passo(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PASSO_EM_SEGUNDOS);
    }

    /** RFC 4226 §5.3, com o contador sendo o passo de tempo (RFC 6238 §4). */
    public static String codigo(byte[] segredo, long passo) {
        byte[] hash = hmacSha1(segredo, ByteBuffer.allocate(8).putLong(passo).array());
        int deslocamento = hash[hash.length - 1] & 0x0f;
        int binario = ((hash[deslocamento] & 0x7f) << 24)
                | ((hash[deslocamento + 1] & 0xff) << 16)
                | ((hash[deslocamento + 2] & 0xff) << 8)
                | (hash[deslocamento + 3] & 0xff);
        return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
    }

    /**
     * Confere o código contra o passo atual e os vizinhos. Um passo já usado, ou anterior a ele, é
     * recusado: quem viu o código na tela de alguém não consegue reaproveitá-lo.
     *
     * @param ultimoPassoUsado o passo do último código aceito deste segredo; nulo se nenhum
     * @return o passo em que o código confere, para ser guardado como o último usado
     */
    public static OptionalLong verificar(byte[] segredo, String informado, Instant agora, Long ultimoPassoUsado) {
        if (informado == null) {
            return OptionalLong.empty();
        }
        String codigo = informado.replaceAll("\\s", "");
        if (!codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long atual = passo(agora);
        for (long passo = atual - TOLERANCIA; passo <= atual + TOLERANCIA; passo++) {
            if (ultimoPassoUsado != null && passo <= ultimoPassoUsado) {
                continue;
            }
            // Comparação em tempo constante, para o tempo de resposta não revelar dígitos certos
            if (MessageDigest.isEqual(codigo(segredo, passo).getBytes(), codigo.getBytes())) {
                return OptionalLong.of(passo);
            }
        }
        return OptionalLong.empty();
    }

    private static byte[] hmacSha1(byte[] chave, byte[] mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            return mac.doFinal(mensagem);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA1 indisponível na JVM.", e);
        }
    }
}
