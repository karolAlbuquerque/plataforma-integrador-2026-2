package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/** "Minha conta" — Requisito RF18. */
class ContaTest extends BaseIntegracao {

    private static final String CONTA = "/api/identity/conta";

    @Test
    void contaMostraTenantPerfisEEquipes() throws Exception {
        mvc.perform(get(CONTA).header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Vendedor da Empresa A"))
                .andExpect(jsonPath("$.data.email").value("vendedor@empresa-a.dev"))
                .andExpect(jsonPath("$.data.tenant.id").value(EMPRESA_A))
                .andExpect(jsonPath("$.data.tenant.nome").value("Empresa A"))
                .andExpect(jsonPath("$.data.perfis[0].nome").value("VENDEDOR"))
                .andExpect(jsonPath("$.data.perfis[0].rotulo").value("Vendedor"))
                .andExpect(jsonPath("$.data.equipes[0].id").value(EQUIPE_COMERCIAL_A))
                .andExpect(jsonPath("$.data.equipes[0].lider").value(false))
                .andExpect(jsonPath("$.data.ultimoLoginEm").isNotEmpty());
    }

    @Test
    void editarNomeETelefoneFicaNaAuditoriaComOValorAnterior() throws Exception {
        String email = emailAleatorio("minha-conta");
        UUID id = criarUsuario(EMPRESA_A, email, "Nome antigo", "TECNICO");
        String token = tokenDe(email);

        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto("  Nome novo  "), "telefone", texto("(81) 99999-0000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Nome novo"))
                .andExpect(jsonPath("$.data.telefone").value("(81) 99999-0000"))
                .andExpect(jsonPath("$.data.email").value(email));

        Map<String, Object> auditoria = jdbc.queryForMap("""
                SELECT usuario_id, valor_anterior::text AS anterior, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'editar' AND entidade = 'usuario' AND entidade_id = ?
                """, id);
        assertThat(auditoria.get("usuario_id")).isEqualTo(id);
        assertThat((String) JsonPath.read((String) auditoria.get("anterior"), "$.nome")).isEqualTo("Nome antigo");
        assertThat((String) JsonPath.read((String) auditoria.get("novo"), "$.nome")).isEqualTo("Nome novo");
        assertThat((String) JsonPath.read((String) auditoria.get("novo"), "$.telefone")).isEqualTo("(81) 99999-0000");

        // Telefone nulo apaga o telefone (o contrato recusa texto vazio); o nome igual não entra na auditoria
        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto("Nome novo"), "telefone", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.telefone").doesNotExist());
        String segunda = jdbc.queryForObject("""
                SELECT valor_novo::text FROM identity.audit_logs
                 WHERE acao = 'editar' AND entidade = 'usuario' AND entidade_id = ?
                   AND valor_novo->'telefone' = 'null'::jsonb
                """, String.class, id);
        assertThat(segunda).doesNotContain("nome");

        // Repetir os mesmos dados não gera auditoria
        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto("Nome novo"))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs WHERE acao = 'editar' AND entidade = 'usuario' AND entidade_id = ?
                """, Integer.class, id)).isEqualTo(2);
    }

    @Test
    void dadosInvalidosRespondem400ComOCampo() throws Exception {
        String token = tokenDe("tecnico@empresa-b.dev");
        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto("Técnico da Empresa B"), "telefone", texto("liga pra mim"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("telefone"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_INVALIDO"));
        // Para apagar o telefone manda-se null; texto vazio não passa no padrão do contrato
        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto("Técnico da Empresa B"), "telefone", texto(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("telefone"));
        mvc.perform(put(CONTA).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", texto(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("nome"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_OBRIGATORIO"));
    }

    @Test
    void tokenDeServicoNaoTemContaESemTokenResponde401() throws Exception {
        String corpo = mvc.perform(post("/api/identity/auth/token-servico").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"landing\",\"clientSecret\":\"segredo-do-landing-para-teste\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenDeServico = JsonPath.read(corpo, "$.data.accessToken");

        mvc.perform(get(CONTA).header("Authorization", bearer(tokenDeServico)).header("X-Tenant-Id", EMPRESA_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get(CONTA)).andExpect(status().isUnauthorized());
    }
}
