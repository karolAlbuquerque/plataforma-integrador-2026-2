package br.com.plataforma.identity.email;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modelo de e-mail em texto: a primeira linha é "Assunto: ...", depois uma linha em branco e o
 * corpo, com variáveis {{nome}} (formato em emails/README.md do infra). Toda variável do modelo
 * precisa vir preenchida — melhor recusar do que mandar um e-mail com buracos.
 */
public record ModeloDeEmail(String nome, String assunto, String corpo, Set<String> variaveis) {

    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}");
    private static final Pattern ASSUNTO = Pattern.compile("^(?i:assunto):\\s*(\\S.*)$");

    public record EmailMontado(String assunto, String corpo) {
    }

    /** @throws IllegalArgumentException com o motivo, se o texto não seguir o formato */
    public static ModeloDeEmail ler(String nome, String texto) {
        String normalizado = texto.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        String[] linhas = normalizado.split("\n", -1);
        Matcher assunto = ASSUNTO.matcher(linhas[0].strip());
        if (!assunto.matches()) {
            throw new IllegalArgumentException("a primeira linha precisa ser 'Assunto: ...'");
        }
        if (linhas.length < 3 || !linhas[1].isBlank()) {
            throw new IllegalArgumentException("depois do assunto vem uma linha em branco e o corpo");
        }
        String corpo = String.join("\n", Arrays.copyOfRange(linhas, 2, linhas.length)).stripTrailing();
        if (corpo.isBlank()) {
            throw new IllegalArgumentException("o corpo está vazio");
        }
        Set<String> variaveis = new TreeSet<>();
        for (String parte : new String[] {assunto.group(1), corpo}) {
            Matcher variavel = VARIAVEL.matcher(parte);
            while (variavel.find()) {
                variaveis.add(variavel.group(1));
            }
        }
        return new ModeloDeEmail(nome, assunto.group(1).strip(), corpo, Set.copyOf(variaveis));
    }

    /** @throws IllegalArgumentException listando as variáveis que faltaram */
    public EmailMontado montar(Map<String, String> valores) {
        Set<String> faltando = new TreeSet<>();
        for (String variavel : variaveis) {
            String valor = valores.get(variavel);
            if (valor == null || valor.isBlank()) {
                faltando.add(variavel);
            }
        }
        if (!faltando.isEmpty()) {
            throw new IllegalArgumentException("faltam as variáveis " + String.join(", ", faltando)
                    + " do modelo " + nome);
        }
        // Quebra de linha no assunto viraria cabeçalho novo no e-mail
        String assuntoPronto = substituir(assunto, valores).replaceAll("[\\r\\n]+", " ").strip();
        return new EmailMontado(assuntoPronto, substituir(corpo, valores));
    }

    private static String substituir(String texto, Map<String, String> valores) {
        return VARIAVEL.matcher(texto).replaceAll(variavel ->
                Matcher.quoteReplacement(valores.get(variavel.group(1)).strip()));
    }
}
