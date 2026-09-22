package br.com.plataforma.identity.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public record TenantResolvido(UUID id, String nome) {
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
}
