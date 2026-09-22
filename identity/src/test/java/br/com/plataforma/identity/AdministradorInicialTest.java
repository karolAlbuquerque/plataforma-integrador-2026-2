package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.plataforma.identity.inicializacao.AdministradorInicial;

/**
 * Primeiro administrador do tenant de produção, por ADMIN_INICIAL_EMAIL (aprovado em 21/09/2026).
 * A base dos testes define a variável: a subida já criou o administrador e mandou o convite.
 */
class AdministradorInicialTest extends BaseIntegracao {

    private static final String EMAIL = "admin-inicial@centinela.dev";

    @Autowired
    AdministradorInicial administradorInicial;

    @Test
    void tenantDeProducaoEDaCentinela() {
        Map<String, Object> tenant = jdbc.queryForMap(
                "SELECT razao_social, nome_fantasia, subdominio FROM identity.tenants WHERE id = ?::uuid", TENANT_DE_PRODUCAO);
        assertThat(tenant).containsEntry("razao_social", "Centinela Soluções")
                .containsEntry("nome_fantasia", "Centinela Soluções")
                .containsEntry("subdominio", "centinela");
    }

    @Test
    void subidaCriaOAdministradorSemSenhaEComConvite() {
        Map<String, Object> usuario = jdbc.queryForMap("""
                SELECT u.nome, u.senha_hash, u.ativo, p.nome AS perfil
                  FROM identity.usuarios u
                  JOIN identity.usuario_perfis up ON up.usuario_id = u.id
                  JOIN identity.perfis p ON p.id = up.perfil_id
                 WHERE u.email = ?::citext AND u.tenant_id = ?::uuid
                """, EMAIL, TENANT_DE_PRODUCAO);
        assertThat(usuario).containsEntry("nome", "Administrador da Centinela")
                .containsEntry("ativo", true)
                .containsEntry("perfil", "ADMINISTRADOR");
        assertThat(usuario.get("senha_hash")).isNull();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.recuperacoes_senha r JOIN identity.usuarios u ON u.id = r.usuario_id
                 WHERE u.email = ?::citext AND r.tipo = 'convite' AND r.usado_em IS NULL
                   AND r.expira_em > now() + interval '71 hours'
                """, Integer.class, EMAIL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs
                 WHERE acao = 'criar' AND tenant_id = ?::uuid AND valor_novo->>'origem' = 'ADMIN_INICIAL_EMAIL'
                """, Integer.class, TENANT_DE_PRODUCAO)).isEqualTo(1);
    }

    @Test
    void rodarDeNovoNaoCriaNemConvidaOutraVez() throws Exception {
        administradorInicial.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.usuarios WHERE tenant_id = ?::uuid",
                Integer.class, TENANT_DE_PRODUCAO)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.recuperacoes_senha r JOIN identity.usuarios u ON u.id = r.usuario_id
                 WHERE u.email = ?::citext
                """, Integer.class, EMAIL)).isEqualTo(1);
        assertThat(emailsPara(EMAIL)).isEmpty();
    }
}
