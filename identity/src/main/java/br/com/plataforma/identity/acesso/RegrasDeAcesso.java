package br.com.plataforma.identity.acesso;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.UsuarioConsultas;

/**
 * Regras que protegem a administração de acesso de si mesma (aprovadas em 21/09/2026):
 *
 * - sem escalada de privilégio: só se concede o que se tem, e só se mexe em quem não tem mais
 *   permissões do que você;
 * - o tenant nunca fica sem administrador — alguém ativo com identity.usuario.administrar e
 *   identity.perfil.administrar.
 *
 * A autoproteção (não desativar a si mesmo nem mudar os próprios perfis) fica em quem administra
 * usuários, que é onde ela se aplica.
 */
@Component
public class RegrasDeAcesso {

    public static final List<String> ADMINISTRACAO = List.of("identity.usuario.administrar", "identity.perfil.administrar");

    /** Primeira chave da trava consultiva do Postgres; a segunda é o tenant. */
    private static final int TRAVA_DE_ADMINISTRACAO = 20_260_921;

    private final JdbcTemplate jdbc;

    public RegrasDeAcesso(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Permissões somadas de um conjunto de perfis do tenant. */
    public Set<String> permissoesDosPerfis(UUID tenant, Collection<UUID> perfis) {
        if (perfis.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT pe.codigo
                  FROM identity.perfis p
                  JOIN identity.perfil_permissoes pp ON pp.perfil_id = p.id
                  JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                 WHERE p.tenant_id = ? AND p.deleted_at IS NULL AND p.id = ANY(?::uuid[])
                """, String.class, tenant, UsuarioConsultas.comoArray(List.copyOf(perfis))));
    }

    /** Permissões efetivas de um usuário, como iriam para o token na próxima renovação. */
    public Set<String> permissoesDoUsuario(UUID tenant, UUID usuario) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT pe.codigo
                  FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id AND p.tenant_id = ? AND p.deleted_at IS NULL
                  JOIN identity.perfil_permissoes pp ON pp.perfil_id = p.id
                  JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                 WHERE up.usuario_id = ?
                """, String.class, tenant, usuario));
    }

    /** @throws ErroDeNegocio PERMISSAO_NAO_CONCEDIVEL se o ator não tem alguma das permissões */
    public void exigirQueTenha(Ator ator, Collection<String> permissoes, String campo, String contexto) {
        Set<String> faltando = ator.semAcessoA(permissoes);
        if (!faltando.isEmpty()) {
            throw ErroDeNegocio.regra(campo, "PERMISSAO_NAO_CONCEDIVEL", contexto + " Você não tem: "
                    + String.join(", ", faltando.stream().limit(5).toList())
                    + (faltando.size() > 5 ? " e mais " + (faltando.size() - 5) + "." : "."));
        }
    }

    /**
     * Chamada depois das escritas, dentro da mesma transação: se a mudança deixou o tenant sem
     * administrador, a exceção desfaz tudo.
     */
    public void exigirAdministradorRestante(UUID tenant, String campo) {
        // Uma checagem por vez no tenant, até o fim da transação. Sem a trava, dois administradores
        // tirando o acesso um do outro ao mesmo tempo passariam os dois — cada um ainda enxergaria
        // o outro — e a empresa ficaria sem ninguém. Com ela, a segunda espera a primeira terminar
        // e a contagem (READ COMMITTED: um retrato novo por comando) já inclui o que a primeira gravou.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?::text))", Object.class,
                TRAVA_DE_ADMINISTRACAO, tenant.toString());
        Integer administradores = jdbc.queryForObject("""
                SELECT count(*) FROM identity.usuarios u
                 WHERE u.tenant_id = ? AND u.ativo AND u.deleted_at IS NULL
                   AND (SELECT count(DISTINCT pe.codigo)
                          FROM identity.usuario_perfis up
                          JOIN identity.perfis p ON p.id = up.perfil_id AND p.deleted_at IS NULL
                          JOIN identity.perfil_permissoes pp ON pp.perfil_id = p.id
                          JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                         WHERE up.usuario_id = u.id AND pe.codigo = ANY(string_to_array(?, ','))) = ?
                """, Integer.class, tenant, String.join(",", ADMINISTRACAO), ADMINISTRACAO.size());
        if (administradores == null || administradores == 0) {
            throw ErroDeNegocio.regra(campo, "ULTIMO_ADMINISTRADOR",
                    "A empresa ficaria sem ninguém que administre usuários e perfis.");
        }
    }
}
