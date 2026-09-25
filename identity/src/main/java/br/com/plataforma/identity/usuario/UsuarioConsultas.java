package br.com.plataforma.identity.usuario;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.inicializacao.PerfisIniciais;

/**
 * Leitura de usuários para as telas (Requisito RF17). Toda consulta recebe o tenant e filtra por
 * ele — a listagem nunca traz usuário de outro tenant, mesmo com o filtro em branco. Perfis e
 * equipes vêm numa consulta só para a página inteira, sem N+1 (RNF02).
 *
 * A busca usa ILIKE sem índice trigram: o pg_trgm exigiria mudar o db/init do infra, e com um
 * tenant de dezenas de usuários o índice por tenant basta.
 */
@Repository
public class UsuarioConsultas {

    public static final String SITUACAO_ATIVO = "ativo";
    public static final String SITUACAO_CONVITE_PENDENTE = "convite_pendente";
    public static final String SITUACAO_CONVITE_EXPIRADO = "convite_expirado";
    public static final String SITUACAO_INATIVO = "inativo";
    public static final String SITUACAO_ANONIMIZADO = "anonimizado";

    public record PerfilDoUsuario(UUID id, String nome, String rotulo, boolean sistema) {
    }

    public record EquipeDoUsuario(UUID id, String nome, boolean lider) {
    }

    public record UsuarioDaLista(UUID id, String nome, String email, String telefone, String situacao,
                                 List<PerfilDoUsuario> perfis, Instant ultimoLoginEm, Instant criadoEm) {
    }

    public record UsuarioDetalhe(UUID id, String nome, String email, String telefone, String situacao,
                                 List<PerfilDoUsuario> perfis, Instant ultimoLoginEm, Instant criadoEm,
                                 List<EquipeDoUsuario> equipes, Instant conviteExpiraEm, boolean segundoFatorAtivo) {
    }

    public record Filtro(String busca, UUID perfilId, String situacao) {
    }

    /** Situação calculada: "ativo" é quem já definiu senha; sem senha, depende do convite. */
    private static final String SELECAO = """
            SELECT u.id, u.nome, u.email::text AS email, u.telefone, u.ultimo_login_em, u.created_at, u.mfa_ativo,
                   CASE WHEN u.anonimizado_em IS NOT NULL THEN 'anonimizado'
                        WHEN NOT u.ativo THEN 'inativo'
                        WHEN u.senha_hash IS NOT NULL THEN 'ativo'
                        WHEN EXISTS (SELECT 1 FROM identity.recuperacoes_senha r
                                      WHERE r.usuario_id = u.id AND r.tipo = 'convite'
                                        AND r.usado_em IS NULL AND r.expira_em > now()) THEN 'convite_pendente'
                        ELSE 'convite_expirado' END AS situacao
              FROM identity.usuarios u
             WHERE u.tenant_id = ? AND u.deleted_at IS NULL
            """;

    /** Só colunas desta lista entram no ORDER BY — o texto do cliente nunca vai para o SQL. */
    private static final Map<String, String> ORDENACAO = Map.of(
            "nome", "lower(x.nome)",
            "email", "x.email",
            "ultimoLoginEm", "x.ultimo_login_em",
            "criadoEm", "x.created_at");

    private final JdbcTemplate jdbc;

    public UsuarioConsultas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<UsuarioDaLista> listar(UUID tenant, Filtro filtro, int pagina, int tamanho, String ordenar) {
        String ordem = clausulaDeOrdem(ordenar);
        StringBuilder onde = new StringBuilder(" WHERE true");
        List<Object> parametros = new ArrayList<>(List.of(tenant));
        if (filtro.busca() != null && !filtro.busca().isBlank()) {
            onde.append(" AND (x.nome ILIKE ? ESCAPE '\\' OR x.email ILIKE ? ESCAPE '\\')");
            String trecho = "%" + escaparLike(filtro.busca().strip()) + "%";
            parametros.add(trecho);
            parametros.add(trecho);
        }
        if (filtro.perfilId() != null) {
            onde.append(" AND EXISTS (SELECT 1 FROM identity.usuario_perfis up WHERE up.usuario_id = x.id AND up.perfil_id = ?)");
            parametros.add(filtro.perfilId());
        }
        if (filtro.situacao() != null) {
            onde.append(" AND x.situacao = ?");
            parametros.add(filtro.situacao());
        }
        String base = "FROM (" + SELECAO + ") x" + onde;

        Long total = jdbc.queryForObject("SELECT count(*) " + base, Long.class, parametros.toArray());
        List<Object> daPagina = new ArrayList<>(parametros);
        daPagina.add(tamanho);
        daPagina.add((long) pagina * tamanho);
        List<Linha> linhas = jdbc.query("SELECT x.* " + base + " ORDER BY " + ordem + ", x.id LIMIT ? OFFSET ?",
                UsuarioConsultas::linha, daPagina.toArray());

        Map<UUID, List<PerfilDoUsuario>> perfis = perfisDe(linhas.stream().map(Linha::id).toList());
        List<UsuarioDaLista> itens = linhas.stream()
                .map(l -> new UsuarioDaLista(l.id(), l.nome(), l.email(), l.telefone(), l.situacao(),
                        perfis.getOrDefault(l.id(), List.of()), l.ultimoLoginEm(), l.criadoEm()))
                .toList();
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    public Optional<UsuarioDetalhe> detalhe(UUID tenant, UUID id) {
        return jdbc.query("SELECT x.* FROM (" + SELECAO + ") x WHERE x.id = ?", UsuarioConsultas::linha, tenant, id)
                .stream().findFirst()
                .map(l -> new UsuarioDetalhe(l.id(), l.nome(), l.email(), l.telefone(), l.situacao(),
                        perfisDe(List.of(l.id())).getOrDefault(l.id(), List.of()), l.ultimoLoginEm(), l.criadoEm(),
                        equipesDe(tenant, l.id()), conviteExpiraEm(l), l.segundoFatorAtivo()));
    }

    public Map<UUID, List<PerfilDoUsuario>> perfisDe(List<UUID> usuarios) {
        if (usuarios.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PerfilDoUsuario>> perfis = new HashMap<>();
        jdbc.query("""
                SELECT up.usuario_id, p.id, p.nome, p.sistema
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id AND p.deleted_at IS NULL
                 WHERE up.usuario_id = ANY(?::uuid[])
                 ORDER BY p.sistema DESC, lower(p.nome)
                """, rs -> {
            perfis.computeIfAbsent(rs.getObject("usuario_id", UUID.class), chave -> new ArrayList<>())
                    .add(new PerfilDoUsuario(rs.getObject("id", UUID.class), rs.getString("nome"),
                            rotulo(rs.getString("nome"), rs.getBoolean("sistema")), rs.getBoolean("sistema")));
        }, comoArray(usuarios));
        return perfis;
    }

    public List<EquipeDoUsuario> equipesDe(UUID tenant, UUID usuario) {
        return jdbc.query("""
                SELECT e.id, e.nome, ue.lider
                  FROM identity.usuario_equipes ue
                  JOIN identity.equipes e ON e.id = ue.equipe_id AND e.deleted_at IS NULL
                 WHERE ue.usuario_id = ? AND e.tenant_id = ?
                 ORDER BY lower(e.nome)
                """, (rs, linha) -> new EquipeDoUsuario(rs.getObject("id", UUID.class), rs.getString("nome"),
                rs.getBoolean("lider")), usuario, tenant);
    }

    /** "PRE_VENDAS" vira "Pré-vendas" nos perfis de sistema; o personalizado já tem o nome de exibição. */
    public static String rotulo(String nome, boolean sistema) {
        if (!sistema) {
            return nome;
        }
        return PerfisIniciais.PERFIS.stream().filter(perfil -> perfil.nome().equals(nome))
                .map(PerfisIniciais.PerfilDeSistema::rotulo).findFirst().orElse(nome);
    }

    /** "{a,b,c}" para ANY(?::uuid[]) — só UUIDs, que não precisam de escape. */
    public static String comoArray(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    private Instant conviteExpiraEm(Linha linha) {
        if (!linha.situacao().startsWith("convite")) {
            return null;
        }
        return jdbc.queryForList("""
                SELECT max(expira_em) FROM identity.recuperacoes_senha
                 WHERE usuario_id = ? AND tipo = 'convite' AND usado_em IS NULL
                """, OffsetDateTime.class, linha.id()).stream()
                .filter(Objects::nonNull).map(OffsetDateTime::toInstant).findFirst().orElse(null);
    }

    static String clausulaDeOrdem(String ordenar) {
        String[] partes = (ordenar == null || ordenar.isBlank() ? "nome,asc" : ordenar).split(",", -1);
        String coluna = ORDENACAO.get(partes[0].strip());
        String direcao = partes.length == 2 ? partes[1].strip().toLowerCase() : "asc";
        if (coluna == null || partes.length > 2 || !(direcao.equals("asc") || direcao.equals("desc"))) {
            throw ErroDeNegocio.invalido("ordenar", "CAMPO_INVALIDO",
                    "Ordene por nome, email, ultimoLoginEm ou criadoEm, com asc ou desc.");
        }
        return coluna + " " + direcao + " NULLS LAST";
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record Linha(UUID id, String nome, String email, String telefone, String situacao, Instant ultimoLoginEm,
                         Instant criadoEm, boolean segundoFatorAtivo) {
    }

    private static Linha linha(ResultSet rs, int numero) throws SQLException {
        OffsetDateTime ultimoLogin = rs.getObject("ultimo_login_em", OffsetDateTime.class);
        return new Linha(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("email"),
                rs.getString("telefone"), rs.getString("situacao"),
                ultimoLogin == null ? null : ultimoLogin.toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("mfa_ativo"));
    }
}
