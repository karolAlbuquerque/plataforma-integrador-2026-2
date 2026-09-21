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
                   (t.ativo AND t.deleted_at IS NULL) AS tenant_ativo
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
        List<String> perfis = jdbc.queryForList("""
                SELECT p.nome
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id
                 WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL
                 ORDER BY p.nome
                """, String.class, usuario.id(), usuario.tenantId());

        List<String> permissoes = jdbc.queryForList("""
                SELECT DISTINCT pe.codigo
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id
                  JOIN identity.perfil_permissoes pp ON pp.perfil_id = p.id
                  JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                 WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL
                 ORDER BY pe.codigo
                """, String.class, usuario.id(), usuario.tenantId());

        List<UUID> equipes = jdbc.queryForList("""
                SELECT e.id
                  FROM identity.usuario_equipes ue
                  JOIN identity.equipes e ON e.id = ue.equipe_id
                 WHERE ue.usuario_id = ? AND e.tenant_id = ? AND e.deleted_at IS NULL
                 ORDER BY e.id
                """, UUID.class, usuario.id(), usuario.tenantId());

        return new UsuarioAutenticado(usuario.id(), usuario.tenantId(), usuario.nome(), usuario.email(),
                perfis, permissoes, equipes);
    }

    public void registrarLogin(UUID id) {
        jdbc.update("UPDATE identity.usuarios SET ultimo_login_em = now() WHERE id = ?", id);
    }

    /** Destinatário de notificação precisa existir e ser do tenant da mensagem. */
    public boolean pertenceAoTenant(UUID usuario, UUID tenant) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.usuarios
                                WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL)
                """, Boolean.class, usuario, tenant);
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
                rs.getBoolean("tenant_ativo"));
    }
}
