package br.com.plataforma.identity.usuario;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas do login e da montagem do token. Aqui o tenant ainda não está no contexto: ele sai
 * do próprio usuário, e toda junção com perfis e equipes confere que são do mesmo tenant.
 */
@Repository
public class UsuarioRepositorio {

    private static final String SELECAO = """
            SELECT u.id, u.tenant_id, u.nome, u.email::text AS email, u.senha_hash, u.ativo,
                   (t.ativo AND t.deleted_at IS NULL) AS tenant_ativo, u.mfa_ativo, u.preferencia_tema
              FROM identity.usuarios u
              JOIN identity.tenants t ON t.id = u.tenant_id
            """;

    private final JdbcTemplate jdbc;

    public UsuarioRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** E-mail comparado sem diferença de maiúsculas (citext), só entre usuários não excluídos. */
    public Optional<UsuarioDeLogin> buscarPorEmail(String email) {
        return jdbc.query(SELECAO + " WHERE u.email = ?::citext AND u.deleted_at IS NULL",
                UsuarioRepositorio::deLogin, email).stream().findFirst();
    }

    public Optional<UsuarioDeLogin> buscarPorId(UUID id) {
        return jdbc.query(SELECAO + " WHERE u.id = ? AND u.deleted_at IS NULL",
                UsuarioRepositorio::deLogin, id).stream().findFirst();
    }

    /** Perfis, permissões (a união das de todos os perfis) e equipes, lidos na hora da emissão. */
    public UsuarioAutenticado carregarAcessos(UsuarioDeLogin usuario) {
        return carregarAcessosNoTenant(usuario, usuario.tenantId());
    }

    /**
     * Acessos do usuário quando opera em {@code tenantAlvo}. Se ele não possui perfis vinculados
     * diretamente a {@code tenantAlvo} (administrador alternando de empresa), mantém os perfis e
     * permissões do seu tenant de origem, com o claim {@code tenant_id} apontando para {@code tenantAlvo}.
     */
    public UsuarioAutenticado carregarAcessosNoTenant(UsuarioDeLogin usuario, UUID tenantAlvo) {
        UUID tenantDosPerfis = tenantAlvo;
        List<String> perfis = perfisNoTenant(usuario.id(), tenantDosPerfis);
        if (perfis.isEmpty() && !tenantAlvo.equals(usuario.tenantId())) {
            tenantDosPerfis = usuario.tenantId();
            perfis = perfisNoTenant(usuario.id(), tenantDosPerfis);
        }

        List<String> permissoes = jdbc.queryForList("""
                SELECT DISTINCT pe.codigo
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id
                  JOIN identity.perfil_permissoes pp ON pp.perfil_id = p.id
                  JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                 WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL
                 ORDER BY pe.codigo
                """, String.class, usuario.id(), tenantDosPerfis);

        List<UUID> equipes = jdbc.queryForList("""
                SELECT e.id
                  FROM identity.usuario_equipes ue
                  JOIN identity.equipes e ON e.id = ue.equipe_id
                 WHERE ue.usuario_id = ? AND e.tenant_id = ? AND e.deleted_at IS NULL
                 ORDER BY e.id
                """, UUID.class, usuario.id(), tenantAlvo);

        return new UsuarioAutenticado(usuario.id(), tenantAlvo, usuario.nome(), usuario.email(),
                perfis, permissoes, equipes);
    }

    private List<String> perfisNoTenant(UUID usuarioId, UUID tenantId) {
        return jdbc.queryForList("""
                SELECT p.nome
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id
                 WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL
                 ORDER BY p.nome
                """, String.class, usuarioId, tenantId);
    }

    /** Vincula o usuário ao perfil ADMINISTRADOR do tenant quando ele ainda não possui nenhum perfil lá. */
    public void vincularComoAdministradorSeSemPerfil(UUID usuarioId, UUID tenantId) {
        Boolean jaTem = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM identity.usuario_perfis up
                      JOIN identity.perfis p ON p.id = up.perfil_id
                     WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL
                )
                """, Boolean.class, usuarioId, tenantId);
        if (!Boolean.TRUE.equals(jaTem)) {
            jdbc.update("""
                    INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                    SELECT ?, p.id FROM identity.perfis p
                     WHERE p.tenant_id = ? AND p.nome = 'ADMINISTRADOR' AND p.sistema AND p.deleted_at IS NULL
                    ON CONFLICT DO NOTHING
                    """, usuarioId, tenantId);
        }
    }

    /** Preferência de tema da conta (Requisito RF40); "sistema" se o usuário não existir mais. */
    public String tema(UUID id) {
        return jdbc.query("SELECT preferencia_tema FROM identity.usuarios WHERE id = ?",
                (rs, linha) -> rs.getString(1), id).stream().findFirst().orElse("sistema");
    }

    public void registrarLogin(UUID id) {
        jdbc.update("UPDATE identity.usuarios SET ultimo_login_em = now() WHERE id = ?", id);
    }

    /** @param autor quem definiu; nulo quando foi pelo link de convite ou de recuperação */
    public void definirSenha(UUID id, String hash, UUID autor) {
        jdbc.update("UPDATE identity.usuarios SET senha_hash = ?, updated_at = now(), updated_by = ? WHERE id = ?",
                hash, autor == null ? id : autor, id);
    }

    /** Destinatário de notificação precisa existir e ser do tenant da mensagem. */
    public boolean pertenceAoTenant(UUID usuario, UUID tenant) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM identity.usuarios u
                     WHERE u.id = ? AND u.deleted_at IS NULL
                       AND (u.tenant_id = ?
                            OR EXISTS (SELECT 1 FROM identity.usuario_perfis up
                                         JOIN identity.perfis p ON p.id = up.perfil_id
                                        WHERE up.usuario_id = u.id AND p.tenant_id = ? AND p.deleted_at IS NULL))
                )
                """, Boolean.class, usuario, tenant, tenant);
        return Boolean.TRUE.equals(existe);
    }

    private static UsuarioDeLogin deLogin(ResultSet rs, int linha) throws SQLException {
        return new UsuarioDeLogin(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getString("senha_hash"),
                rs.getBoolean("ativo"),
                rs.getBoolean("tenant_ativo"),
                rs.getBoolean("mfa_ativo"),
                rs.getString("preferencia_tema"));
    }
}
