package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Administração de empresas contratantes (tenants) e troca de tenant na sessão — Requisito RF27.
 */
class TenantsTest extends BaseIntegracao {

    private static final String TENANTS = "/api/identity/tenants";

    @Test
    void cadastraTenantSemeiaOsDezPerfisEnviaConviteEAlternaNaSessao() throws Exception {
        Sessao admin = entrar("administrador@empresa-a.dev", SENHA, "Chrome/140.0 (Windows NT 10.0)");
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String cnpj = String.format("%014d", Math.abs(sufixo.hashCode()) % 100_000_000_000_000L);
        String subdominio = "filial-" + sufixo;
        String emailAdminNovo = "admin-" + sufixo + "@filial.dev";

        String respostaCadastro = mvc.perform(post(TENANTS)
                        .header("Authorization", admin.autorizacao())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(
                                "razaoSocial", texto("Filial " + sufixo + " Ltda."),
                                "nomeFantasia", texto("Filial " + sufixo),
                                "cnpj", texto(cnpj),
                                "subdominio", texto(subdominio),
                                "adminNome", texto("Responsável da Filial"),
                                "adminEmail", texto(emailAdminNovo))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.tenant.nomeFantasia").value("Filial " + sufixo))
                .andExpect(jsonPath("$.data.tenant.cnpj").value(cnpj))
                .andExpect(jsonPath("$.data.tenant.subdominio").value(subdominio))
                .andExpect(jsonPath("$.data.tenant.totalPerfis").value(10))
                .andExpect(jsonPath("$.data.tenant.totalUsuarios").value(2))
                .andExpect(jsonPath("$.data.conviteEnviado").value(true))
                .andReturn().getResponse().getContentAsString();

        String novoTenantId = JsonPath.read(respostaCadastro, "$.data.tenant.id");

        // Alterna o contexto da sessão para o novo tenant
        MockHttpServletResponse respostaTroca = mvc.perform(post("/api/identity/auth/tenant")
                        .header("Authorization", admin.autorizacao())
                        .cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("tenantId", texto(novoTenantId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.tenantId").value(novoTenantId))
                .andReturn().getResponse();

        String tokenNoNovoTenant = JsonPath.read(respostaTroca.getContentAsString(), "$.data.accessToken");
        Cookie cookieNoNovoTenant = respostaTroca.getCookie("refresh_token");
        assertThat(cookieNoNovoTenant).isNotNull();

        // /auth/me e /usuarios passam a refletir o novo tenant (com quem criou + o admin convidado)
        mvc.perform(get("/api/identity/auth/me").header("Authorization", bearer(tokenNoNovoTenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenant.id").value(novoTenantId))
                .andExpect(jsonPath("$.data.tenant.nome").value("Filial " + sufixo));

        mvc.perform(get("/api/identity/usuarios").header("Authorization", bearer(tokenNoNovoTenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        // Renovar a sessão (F5) mantém o tenant escolhido
        mvc.perform(post("/api/identity/auth/refresh").cookie(cookieNoNovoTenant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.tenantId").value(novoTenantId));
    }

    @Test
    void editaDesativaEReativaTenantImpedindoAutodesativacao() throws Exception {
        String admin = tokenDe("administrador@empresa-a.dev");
        String outroTenant = criarTenant("Filial Desativável");

        mvc.perform(put(TENANTS + "/" + outroTenant)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(
                                "razaoSocial", texto("Filial Editada S.A."),
                                "nomeFantasia", texto("Filial Editada"),
                                "cnpj", texto(null),
                                "subdominio", texto("filial-" + outroTenant.substring(0, 8)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.razaoSocial").value("Filial Editada S.A."))
                .andExpect(jsonPath("$.data.nomeFantasia").value("Filial Editada"));

        // Não pode desativar a empresa em que está conectado
        mvc.perform(post(TENANTS + "/" + EMPRESA_A + "/desativar").header("Authorization", bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("AUTOPROTECAO"));

        // Desativar outra empresa bloqueia a troca de contexto para ela
        mvc.perform(post(TENANTS + "/" + outroTenant + "/desativar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));

        mvc.perform(post("/api/identity/auth/tenant")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("tenantId", texto(outroTenant))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("TENANT_INATIVO"));

        mvc.perform(post(TENANTS + "/" + outroTenant + "/reativar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(true));
    }
}
