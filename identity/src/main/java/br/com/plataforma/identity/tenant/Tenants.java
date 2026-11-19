package br.com.plataforma.identity.tenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.api.Pagina;

/** Empresas contratantes — a raiz do isolamento (Requisito RF27). */
@Repository
public class Tenants {

    private final JdbcTemplate jdbc;

    public Tenants(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> todos() {
        return jdbc.queryForList("SELECT id FROM identity.tenants WHERE deleted_at IS NULL ORDER BY id", UUID.class);
    }

    public boolean existe(UUID id) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identity.tenants WHERE id = ? AND deleted_at IS NULL)", Boolean.class, id);
        return Boolean.TRUE.equals(existe);
    }

    public boolean estaAtivo(UUID id) {
        Boolean ativo = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identity.tenants WHERE id = ? AND ativo AND deleted_at IS NULL)",
                Boolean.class, id);
        return Boolean.TRUE.equals(ativo);
    }

    public record TenantResolvido(UUID id, String nome) {
    }

    public record TenantDaLista(UUID id, String razaoSocial, String nomeFantasia, String cnpj, String subdominio,
                                boolean ativo, int totalUsuarios, boolean atual, Instant criadoEm) {
    }

    public record TenantDetalhe(UUID id, String razaoSocial, String nomeFantasia, String cnpj, String subdominio,
                                boolean ativo, int totalUsuarios, int totalPerfis, int totalEquipes, boolean atual,
                                Instant criadoEm) {
    }

    /** Tenant ativo com o subdomínio, para a superfície pública (Requisito RF31). */
    public Optional<TenantResolvido> ativoPorSubdominio(String subdominio) {
        return jdbc.query("""
                SELECT id, coalesce(nome_fantasia, razao_social) AS nome
                  FROM identity.tenants
                 WHERE subdominio = ? AND ativo AND deleted_at IS NULL
                """, (rs, linha) -> new TenantResolvido(rs.getObject("id", UUID.class), rs.getString("nome")),
                subdominio).stream().findFirst();
    }

    /** Nome exibido na barra da casca: o fantasia, se houver. */
    public Optional<String> nome(UUID id) {
        return jdbc.queryForList(
                "SELECT coalesce(nome_fantasia, razao_social) FROM identity.tenants WHERE id = ? AND deleted_at IS NULL",
                String.class, id).stream().findFirst();
    }

    /** Empresas ativas às quais o usuário pertence (ou todas as ativas, se administra tenants). */
    public List<TenantDaLista> ativosDoUsuario(UUID usuarioId, UUID tenantAtual, boolean administraTodos) {
        String filtro = administraTodos ? "" : """
                 AND (EXISTS (SELECT 1 FROM identity.usuarios u WHERE u.id = ? AND u.tenant_id = t.id AND u.deleted_at IS NULL)
                      OR EXISTS (SELECT 1 FROM identity.usuario_perfis up
                                   JOIN identity.perfis p ON p.id = up.perfil_id
                                  WHERE up.usuario_id = ? AND p.tenant_id = t.id AND p.deleted_at IS NULL))
                """;
        List<Object> params = new ArrayList<>();
        params.add(tenantAtual);
        if (!administraTodos) {
            params.add(usuarioId);
            params.add(usuarioId);
        }
        params.add(tenantAtual);
        return jdbc.query("""
                SELECT t.id, t.razao_social, t.nome_fantasia, t.cnpj, t.subdominio, t.ativo, t.created_at,
                """ + CONTAGEM_DE_USUARIOS + """
                       AS total_usuarios,
                       (t.id = ?) AS atual
                  FROM identity.tenants t
                 WHERE t.deleted_at IS NULL AND t.ativo = true
                """ + filtro + """
                 ORDER BY (t.id = ?) DESC, lower(coalesce(t.nome_fantasia, t.razao_social)), t.id
                """, Tenants::daLista, params.toArray());
    }

    private static final String CONTAGEM_DE_USUARIOS = """
            (SELECT count(*) FROM identity.usuarios u
              WHERE u.deleted_at IS NULL
                AND (u.tenant_id = t.id
                     OR EXISTS (SELECT 1 FROM identity.usuario_perfis up
                                  JOIN identity.perfis p ON p.id = up.perfil_id
                                 WHERE up.usuario_id = u.id AND p.tenant_id = t.id AND p.deleted_at IS NULL)))
            """;

    public Pagina<TenantDaLista> listar(UUID tenantAtual, String busca, String situacao, int pagina, int tamanho) {
        StringBuilder onde = new StringBuilder(" WHERE t.deleted_at IS NULL");
        List<Object> parametros = new ArrayList<>();
        if (busca != null && !busca.isBlank()) {
            onde.append("""
                     AND (t.razao_social ILIKE ? ESCAPE '\\'
                       OR coalesce(t.nome_fantasia, '') ILIKE ? ESCAPE '\\'
                       OR coalesce(t.cnpj, '') ILIKE ? ESCAPE '\\'
                       OR coalesce(t.subdominio, '') ILIKE ? ESCAPE '\\')
                    """);
            String trecho = "%" + escaparLike(busca.strip()) + "%";
            parametros.add(trecho);
            parametros.add(trecho);
            parametros.add(trecho);
            parametros.add(trecho);
        }
        if ("ativo".equals(situacao)) {
            onde.append(" AND t.ativo = true");
        } else if ("inativo".equals(situacao)) {
            onde.append(" AND t.ativo = false");
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM identity.tenants t" + onde, Long.class, parametros.toArray());
        List<Object> daPagina = new ArrayList<>(parametros);
        daPagina.add(tenantAtual);
        daPagina.add(tenantAtual);
        daPagina.add(tamanho);
        daPagina.add((long) pagina * tamanho);

        List<TenantDaLista> itens = jdbc.query("""
                SELECT t.id, t.razao_social, t.nome_fantasia, t.cnpj, t.subdominio, t.ativo, t.created_at,
                """ + CONTAGEM_DE_USUARIOS + """
                       AS total_usuarios,
                       (t.id = ?) AS atual
                  FROM identity.tenants t
                """ + onde + """
                 ORDER BY (t.id = ?) DESC, t.ativo DESC, lower(coalesce(t.nome_fantasia, t.razao_social)), t.id
                 LIMIT ? OFFSET ?
                """, Tenants::daLista, daPagina.toArray());

        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    public Optional<TenantDetalhe> detalhe(UUID id, UUID tenantAtual) {
        return jdbc.query("""
                SELECT t.id, t.razao_social, t.nome_fantasia, t.cnpj, t.subdominio, t.ativo, t.created_at,
                """ + CONTAGEM_DE_USUARIOS + """
                       AS total_usuarios,
                       (SELECT count(*) FROM identity.perfis p WHERE p.tenant_id = t.id AND p.deleted_at IS NULL) AS total_perfis,
                       (SELECT count(*) FROM identity.equipes e WHERE e.tenant_id = t.id AND e.deleted_at IS NULL) AS total_equipes,
                       (t.id = ?) AS atual
                  FROM identity.tenants t
                 WHERE t.id = ? AND t.deleted_at IS NULL
                """, Tenants::detalheLinha, tenantAtual, id).stream().findFirst();
    }

    private static TenantDaLista daLista(ResultSet rs, int linha) throws SQLException {
        return new TenantDaLista(
                rs.getObject("id", UUID.class),
                rs.getString("razao_social"),
                rs.getString("nome_fantasia"),
                rs.getString("cnpj"),
                rs.getString("subdominio"),
                rs.getBoolean("ativo"),
                rs.getInt("total_usuarios"),
                rs.getBoolean("atual"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static TenantDetalhe detalheLinha(ResultSet rs, int linha) throws SQLException {
        return new TenantDetalhe(
                rs.getObject("id", UUID.class),
                rs.getString("razao_social"),
                rs.getString("nome_fantasia"),
                rs.getString("cnpj"),
                rs.getString("subdominio"),
                rs.getBoolean("ativo"),
                rs.getInt("total_usuarios"),
                rs.getInt("total_perfis"),
                rs.getInt("total_equipes"),
                rs.getBoolean("atual"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
