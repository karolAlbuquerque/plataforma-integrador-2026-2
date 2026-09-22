package br.com.plataforma.identity.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Modelos de e-mail de sistema (Requisito RF58).
 *
 * Os internos — convite e recuperação de senha — vêm do classpath (emails/identity/). Os dos
 * módulos vêm de {identity.config.diretorio}/emails/{modulo}/{nome}.txt, entregues por PR no
 * infra, e se chamam "{modulo}.{nome}". Modelo com erro é ignorado com aviso: o PR de um grupo
 * não pode tirar o e-mail do ar para os outros.
 */
@Component
public class ModelosDeEmail {

    private static final Logger log = LoggerFactory.getLogger(ModelosDeEmail.class);

    public static final String CONVITE = "convite";
    public static final String RECUPERACAO = "recuperacao-senha";

    private static final Pattern CODIGO_DE_MODULO = Pattern.compile("^[a-z]+$");
    private static final Pattern NOME_DE_ARQUIVO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*\\.txt$");
    private static final long MAXIMO_EM_BYTES = 20_000;

    private final Map<String, ModeloDeEmail> internos = new HashMap<>();
    private final Map<String, ModeloDeEmail> dosModulos = new HashMap<>();

    public ModelosDeEmail(@Value("${identity.config.diretorio}") String diretorio) throws IOException {
        for (Resource arquivo : new PathMatchingResourcePatternResolver().getResources("classpath:emails/identity/*.txt")) {
            String nome = arquivo.getFilename().replaceFirst("\\.txt$", "");
            try (InputStream entrada = arquivo.getInputStream()) {
                internos.put(nome, ModeloDeEmail.ler("identity." + nome, new String(entrada.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        lerDosModulos(Path.of(diretorio, "emails"));
        log.info("Modelos de e-mail: {} internos e {} dos módulos.", internos.size(), dosModulos.size());
    }

    public ModeloDeEmail interno(String nome) {
        ModeloDeEmail modelo = internos.get(nome);
        if (modelo == null) {
            throw new IllegalStateException("Modelo interno de e-mail ausente: " + nome);
        }
        return modelo;
    }

    /** @param modelo "{modulo}.{nome}" */
    public Optional<ModeloDeEmail> doModulo(String modelo) {
        return Optional.ofNullable(dosModulos.get(modelo));
    }

    private void lerDosModulos(Path pasta) throws IOException {
        if (!Files.isDirectory(pasta)) {
            log.warn("Pasta {} não encontrada: só os modelos internos e o formato genérico.", pasta);
            return;
        }
        try (DirectoryStream<Path> modulos = Files.newDirectoryStream(pasta, Files::isDirectory)) {
            for (Path modulo : modulos) {
                String codigo = modulo.getFileName().toString();
                if (!CODIGO_DE_MODULO.matcher(codigo).matches() || codigo.equals("identity")) {
                    log.error("emails/{}: pasta fora do padrão emails/{{modulo}}/ ou reservada; ignorada.", codigo);
                    continue;
                }
                try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(modulo)) {
                    for (Path arquivo : arquivos) {
                        lerArquivo(codigo, arquivo);
                    }
                }
            }
        }
    }

    private void lerArquivo(String modulo, Path arquivo) {
        String nomeDoArquivo = arquivo.getFileName().toString();
        String caminho = "emails/" + modulo + "/" + nomeDoArquivo;
        try {
            if (!NOME_DE_ARQUIVO.matcher(nomeDoArquivo).matches()) {
                log.error("{}: nome fora do padrão {{nome}}.txt, em minúsculas com hífen; ignorado.", caminho);
                return;
            }
            if (Files.size(arquivo) > MAXIMO_EM_BYTES) {
                log.error("{}: maior que {} bytes; ignorado.", caminho, MAXIMO_EM_BYTES);
                return;
            }
            String nome = modulo + "." + nomeDoArquivo.replaceFirst("\\.txt$", "");
            dosModulos.put(nome, ModeloDeEmail.ler(nome, Files.readString(arquivo, StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException | IOException e) {
            log.error("{}: {}; ignorado.", caminho, e.getMessage());
        }
    }
}
