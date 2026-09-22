package br.com.plataforma.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * Resolver de tenant da superfície pública — Requisito RF31: o módulo que serve a página pergunta,
 * com o token de serviço e sem saber o tenant, a qual empresa pertence o subdomínio acessado.
 */
class SuperficiePublicaTest extends BaseIntegracao {

    private static final String RESOLVER = "/api/identity/tenants/resolver";

    @Test
    void servicoDescobreOTenantPeloSubdominioSemXTenantId() throws Exception {
        String servico = bearer(tokenDoLanding());

        mvc.perform(get(RESOLVER).param("subdominio", "centinela").header("Authorization", servico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TENANT_DE_PRODUCAO))
                .andExpect(jsonPath("$.data.nome").value("Centinela Soluções"));
        mvc.perform(get(RESOLVER).param("subdominio", "Centinela").header("Authorization", servico))
                .andExpect(jsonPath("$.data.id").value(TENANT_DE_PRODUCAO));
    }

    @Test
    void subdominioDesconhecidoOuDeTenantInativoResponde404() throws Exception {
        String servico = bearer(tokenDoLanding());
        String inativo = criarTenant("Inativa");
        jdbc.update("UPDATE identity.tenants SET ativo = false WHERE id = ?::uuid", inativo);
        String subdominio = jdbc.queryForObject("SELECT subdominio FROM identity.tenants WHERE id = ?::uuid",
                String.class, inativo);

        mvc.perform(get(RESOLVER).param("subdominio", subdominio).header("Authorization", servico))
                .andExpect(status().isNotFound());
        mvc.perform(get(RESOLVER).param("subdominio", "nao-existe").header("Authorization", servico))
                .andExpect(status().isNotFound());
        mvc.perform(get(RESOLVER).param("subdominio", "centinela.dev").header("Authorization", servico))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("subdominio"));
    }

    @Test
    void usuarioSemAPermissaoEOutrasRotasDoServicoContinuamComoAntes() throws Exception {
        mvc.perform(get(RESOLVER).param("subdominio", "centinela")
                        .header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isForbidden());
        // A exceção ao X-Tenant-Id vale só para o resolver
        mvc.perform(get("/api/identity/eventos").param("empresaId", EMPRESA_A)
                        .header("Authorization", bearer(tokenDoLanding())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token de serviço exige o cabeçalho X-Tenant-Id com um UUID válido."));
    }

    private String tokenDoLanding() throws Exception {
        String corpo = mvc.perform(post("/api/identity/auth/token-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"landing\",\"clientSecret\":\"segredo-do-landing-para-teste\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.accessToken");
    }
}
