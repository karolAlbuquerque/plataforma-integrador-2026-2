package br.com.plataforma.identity.segundofator;

import java.io.ByteArrayOutputStream;

/**
 * Base32 da RFC 4648, sem padding — o formato do segredo que o usuário digita no autenticador
 * quando não consegue ler o QR Code.
 */
public final class Base32 {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    public static String codificar(byte[] dados) {
        StringBuilder texto = new StringBuilder((dados.length * 8 + 4) / 5);
        int acumulado = 0;
        int bits = 0;
        for (byte b : dados) {
            acumulado = (acumulado << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                texto.append(ALFABETO.charAt((acumulado >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO.charAt((acumulado << (5 - bits)) & 0x1f));
        }
        return texto.toString();
    }

    /** Aceita minúsculas, espaços e "=" no fim, como o usuário costuma copiar. */
    public static byte[] decodificar(String texto) {
        String limpo = texto.replaceAll("[\\s=]", "").toUpperCase();
        ByteArrayOutputStream dados = new ByteArrayOutputStream();
        int acumulado = 0;
        int bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = ALFABETO.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Caractere fora do Base32: " + c);
            }
            acumulado = (acumulado << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                dados.write((acumulado >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return dados.toByteArray();
    }
}
