package br.com.plataforma.identity.inicializacao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import br.com.plataforma.identity.modulo.ModuloRepositorio;
import br.com.plataforma.identity.modulo.ModuloRepositorio.ItemDeMenu;
import br.com.plataforma.identity.modulo.ModuloRepositorio.Modulo;

/**
 * Primeiro passo da subida: carrega o catálogo que os grupos entregam por pull request no infra
 * (Contrato §5.1 e §11) — permissoes/*.yaml e modulos/*.json, montados em identity.config.diretorio.
 *
 * Arquivo inválido é registrado no log e ignorado: um erro no PR de um grupo não pode tirar o
 * login do ar para os oito.
 */
@Component
@Order(1)
public class CatalogoDePermissoes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogoDePermissoes.class);

    private static final Pattern CODIGO_DE_PERMISSAO = Pattern.compile("^[a-z]+\\.(acessar|[a-z_]+\\.[a-z_]+)$");
    private static final Pattern CODIGO_DE_MODULO = Pattern.compile("^[a-z]+$");
    private static final Pattern ICONE = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern ROTA = Pattern.compile("^/[a-z0-9/-]*$");

    record PermissaoDeclarada(String codigo, String descricao, List<String> perfisPadrao, List<String> servicos) {
    }

    record ListaDePermissoes(String modulo, List<PermissaoDeclarada> permissoes) {
    }

    record ItemDeclarado(String rota, String nome, String permissao) {
    }

    record ModuloDeclarado(String codigo, String nome, String grupo, String icone, String urlFrontend,
                           String prefixoApi, String permissaoMenu, Integer ordemMenu, String healthcheck,
                           List<ItemDeclarado> itensSubmenu) {
    }

    private final JdbcTemplate jdbc;
    private final ModuloRepositorio modulos;
    private final TransactionTemplate transacao;
    private final ObjectMapper json;
    private final ObjectMapper yaml;
    private final Path diretorio;

    public CatalogoDePermissoes(JdbcTemplate jdbc, ModuloRepositorio modulos, TransactionTemplate transacao,
                                ObjectMapper json, @Value("${identity.config.diretorio}") String diretorio) {
        this.jdbc = jdbc;
        this.modulos = modulos;
        this.transacao = transacao;
        this.json = json;
        this.yaml = YAMLMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.diretorio = Path.of(diretorio);
    }

    @Override
    public void run(ApplicationArguments argumentos) throws IOException {
        Map<String, ListaDePermissoes> listas = lerListas(diretorio.resolve("permissoes"));
        if (!listas.containsKey("identity")) {
            try (InputStream embutida = new ClassPathResource("catalogo/permissoes/identity.yaml").getInputStream()) {
                listas.put("identity", yaml.readValue(embutida, ListaDePermissoes.class));
            }
        }

        int[] contagem = new int[2];   // total, novas
        transacao.executeWithoutResult(status -> listas.values().forEach(lista -> {
            for (PermissaoDeclarada permissao : Objects.requireNonNullElse(lista.permissoes(), List.<PermissaoDeclarada>of())) {
                if (valida(lista.modulo(), permissao)) {
                    contagem[0]++;
                    if (salvar(permissao)) {
                        contagem[1]++;
                    }
                }
            }
        }));

        int registrados = carregarModulos(diretorio.resolve("modulos"));
        log.info("Catálogo carregado: {} permissões de {} módulos ({} novas) e {} registros de menu.",
                contagem[0], listas.size(), contagem[1], registrados);
    }

    private Map<String, ListaDePermissoes> lerListas(Path pasta) throws IOException {
        Map<String, ListaDePermissoes> listas = new TreeMap<>();
        if (!Files.isDirectory(pasta)) {
            log.warn("Pasta {} não encontrada: só as permissões do identity serão carregadas.", pasta);
            return listas;
        }
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pasta, "*.{yaml,yml}")) {
            for (Path arquivo : arquivos) {
                try {
                    ListaDePermissoes lista = yaml.readValue(arquivo.toFile(), ListaDePermissoes.class);
                    if (lista.modulo() == null || !CODIGO_DE_MODULO.matcher(lista.modulo()).matches()) {
                        log.error("{}: campo 'modulo' ausente ou inválido; arquivo ignorado.", arquivo.getFileName());
                    } else {
                        listas.put(lista.modulo(), lista);
                    }
                } catch (IOException e) {
                    log.error("{}: não foi possível ler ({}); arquivo ignorado.", arquivo.getFileName(), e.getMessage());
                }
            }
        }
        return listas;
    }

    /** Um módulo só declara permissão com o próprio prefixo; perfil desconhecido é descartado. */
    private boolean valida(String modulo, PermissaoDeclarada permissao) {
        if (permissao.codigo() == null || !CODIGO_DE_PERMISSAO.matcher(permissao.codigo()).matches()
                || !permissao.codigo().startsWith(modulo + ".")) {
            log.error("Permissão '{}' da lista {} fora do formato {}.recurso.acao; ignorada.",
                    permissao.codigo(), modulo, modulo);
            return false;
        }
        if (permissao.descricao() == null || permissao.descricao().isBlank()) {
            log.error("Permissão {} sem descrição; ignorada.", permissao.codigo());
            return false;
        }
        for (String perfil : Objects.requireNonNullElse(permissao.perfisPadrao(), List.<String>of())) {
            if (!PerfisIniciais.NOMES.contains(perfil)) {
                log.warn("Permissão {}: perfil '{}' não existe e foi ignorado.", permissao.codigo(), perfil);
            }
        }
        return true;
    }

    /**
     * @return true se a permissão é nova — nesse caso, os perfis de sistema de todos os tenants
     *         que ela lista em perfisPadrao passam a tê-la. Permissão que já existia não muda de
     *         perfil: depois de criada, quem decide é o administrador do tenant.
     */
    private boolean salvar(PermissaoDeclarada permissao) {
        String[] partes = permissao.codigo().split("\\.");
        String recurso = partes.length == 2 ? "modulo" : partes[1];
        String acao = partes[partes.length - 1];
        String perfis = String.join(",", Objects.requireNonNullElse(permissao.perfisPadrao(), List.<String>of())
                .stream().filter(PerfisIniciais.NOMES::contains).toList());
        String servicos = String.join(",", Objects.requireNonNullElse(permissao.servicos(), List.<String>of())
                .stream().filter(servico -> CODIGO_DE_MODULO.matcher(servico).matches()).toList());

        record Resultado(UUID id, boolean inserida) {
        }
        Resultado resultado = jdbc.queryForObject("""
                INSERT INTO identity.permissoes (id, codigo, modulo, recurso, acao, descricao, perfis_padrao, servicos)
                VALUES (?, ?, ?, ?, ?, ?, string_to_array(?, ','), string_to_array(?, ','))
                ON CONFLICT (codigo) DO UPDATE
                   SET descricao = EXCLUDED.descricao,
                       perfis_padrao = EXCLUDED.perfis_padrao,
                       servicos = EXCLUDED.servicos
                RETURNING id, (xmax = 0) AS inserida
                """, (rs, linha) -> new Resultado(rs.getObject("id", UUID.class), rs.getBoolean("inserida")),
                UUID.randomUUID(), permissao.codigo(), partes[0], recurso, acao, permissao.descricao().strip(),
                perfis, servicos);

        if (resultado != null && resultado.inserida() && !perfis.isEmpty()) {
            jdbc.update("""
                    INSERT INTO identity.perfil_permissoes (perfil_id, permissao_id)
                    SELECT p.id, ? FROM identity.perfis p
                     WHERE p.sistema AND p.deleted_at IS NULL AND p.nome = ANY(string_to_array(?, ','))
                    ON CONFLICT DO NOTHING
                    """, resultado.id(), perfis);
        }
        return resultado != null && resultado.inserida();
    }

    private int carregarModulos(Path pasta) throws IOException {
        if (!Files.isDirectory(pasta)) {
            log.warn("Pasta {} não encontrada: nenhum módulo no menu.", pasta);
            return 0;
        }
        int registrados = 0;
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pasta, "*.json")) {
            for (Path arquivo : arquivos) {
                if (arquivo.getFileName().toString().equals("schema.json")) {
                    continue;
                }
                try {
                    ModuloDeclarado declarado = json.readValue(arquivo.toFile(), ModuloDeclarado.class);
                    String problema = problemaDo(declarado);
                    if (problema != null) {
                        log.error("{}: {}; registro ignorado.", arquivo.getFileName(), problema);
                        continue;
                    }
                    modulos.salvar(paraModulo(declarado));
                    registrados++;
                } catch (IOException e) {
                    log.error("{}: não foi possível ler ({}); registro ignorado.", arquivo.getFileName(), e.getMessage());
                }
            }
        }
        return registrados;
    }

    /**
     * O gateway roteia por /modulos/{codigo}/ e /api/{codigo}/ (Contrato §12.8): caminhos que não
     * batem com o código do módulo levariam o menu a um endereço que não existe.
     */
    private static String problemaDo(ModuloDeclarado modulo) {
        String codigo = modulo.codigo();
        if (codigo == null || !CODIGO_DE_MODULO.matcher(codigo).matches()) {
            return "código ausente ou inválido";
        }
        if (modulo.nome() == null || modulo.nome().isBlank()) {
            return "nome ausente";
        }
        if (!("/modulos/" + codigo + "/").equals(modulo.urlFrontend())) {
            return "urlFrontend deveria ser /modulos/" + codigo + "/";
        }
        if (!("/api/" + codigo).equals(modulo.prefixoApi())) {
            return "prefixoApi deveria ser /api/" + codigo;
        }
        if (!(codigo + ".acessar").equals(modulo.permissaoMenu())) {
            return "permissaoMenu deveria ser " + codigo + ".acessar";
        }
        if (!("/api/" + codigo + "/health").equals(modulo.healthcheck())) {
            return "healthcheck deveria ser /api/" + codigo + "/health";
        }
        for (ItemDeclarado item : Objects.requireNonNullElse(modulo.itensSubmenu(), List.<ItemDeclarado>of())) {
            if (item.rota() == null || !ROTA.matcher(item.rota()).matches()
                    || item.nome() == null || item.nome().isBlank()
                    || item.permissao() == null || !CODIGO_DE_PERMISSAO.matcher(item.permissao()).matches()) {
                return "item de submenu inválido: " + item;
            }
        }
        return null;
    }

    private static Modulo paraModulo(ModuloDeclarado declarado) {
        List<ItemDeMenu> itens = new ArrayList<>();
        for (ItemDeclarado item : Objects.requireNonNullElse(declarado.itensSubmenu(), List.<ItemDeclarado>of())) {
            itens.add(new ItemDeMenu(item.rota(), item.nome().strip(), item.permissao()));
        }
        String icone = declarado.icone() != null && ICONE.matcher(declarado.icone()).matches() ? declarado.icone() : null;
        int ordem = declarado.ordemMenu() == null ? 100 : Math.clamp(declarado.ordemMenu(), 1, 999);
        return new Modulo(declarado.codigo(), declarado.nome().strip(), declarado.grupo(), icone,
                declarado.urlFrontend(), declarado.prefixoApi(), declarado.permissaoMenu(), ordem,
                declarado.healthcheck(), itens);
    }
}
