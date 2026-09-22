package br.com.plataforma.identity.autenticacao;

import java.util.List;
import java.util.Map;

/**
 * Descrição curta do User-Agent para a tela de sessões ativas: "Chrome no Windows". Aproximada de
 * propósito — serve para a pessoa reconhecer o dispositivo, não para identificá-lo.
 */
final class Navegador {

    /** A ordem importa: o Edge e o Opera também dizem "Chrome", e o Chrome também diz "Safari". */
    private static final List<Map.Entry<String, String>> NAVEGADORES = List.of(
            Map.entry("Edg/", "Edge"),
            Map.entry("OPR/", "Opera"),
            Map.entry("Firefox/", "Firefox"),
            Map.entry("Chrome/", "Chrome"),
            Map.entry("Safari/", "Safari"));

    private static final List<Map.Entry<String, String>> SISTEMAS = List.of(
            Map.entry("Windows", "Windows"),
            Map.entry("Android", "Android"),
            Map.entry("iPhone", "iOS"),
            Map.entry("iPad", "iPadOS"),
            Map.entry("Mac OS X", "macOS"),
            Map.entry("Linux", "Linux"));

    private Navegador() {
    }

    static String descrever(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String navegador = primeiro(NAVEGADORES, userAgent);
        String sistema = primeiro(SISTEMAS, userAgent);
        if (navegador == null && sistema == null) {
            return userAgent.length() > 40 ? userAgent.substring(0, 40) + "…" : userAgent;
        }
        if (navegador == null) {
            return "Navegador no " + sistema;
        }
        return sistema == null ? navegador : navegador + " no " + sistema;
    }

    private static String primeiro(List<Map.Entry<String, String>> opcoes, String texto) {
        return opcoes.stream().filter(opcao -> texto.contains(opcao.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
