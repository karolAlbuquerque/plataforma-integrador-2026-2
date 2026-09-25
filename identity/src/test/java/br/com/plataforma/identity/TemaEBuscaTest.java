package br.com.plataforma.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Tema na conta (Requisito RF40) e a parte do identity na busca global (RF55, Contrato §8.6). */
class TemaEBuscaTest extends BaseIntegracao {

    @Test
    void temaFicaNaContaEVoltaNoLoginNaRenovacaoENoMe() throws Exception {
        String email = emailAleatorio("tema");
        criarUsuario(EMPRESA_A, email, "Tereza do Tema", "VENDEDOR");
        Sessao sessao = entrar(email, SENHA, "Navegador de teste");

        mvc.perform(get("/api/identity/conta").header("Authorization", sessao.autorizacao()))
                .andExpect(jsonPath("$.data.tema").value("sistema"))
                .andExpect(jsonPath("$.data.segundoFatorAtivo").value(false));

        mvc.perform(put("/api/identity/conta/tema").header("Authorization", sessao.autorizacao())
                        .contentType(MediaType.APPLICATION_JSON).content(json("tema", texto("escuro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tema").value("escuro"));
        mvc.perform(put("/api/identity/conta/tema").header("Authorization", sessao.autorizacao())
                        .contentType(MediaType.APPLICATION_JSON).content(json("tema", texto("roxo"))))
                .andExpect(status().isBadRequest());

        // Outro navegador, outra sessão: o tema vem junto
        mvc.perform(login(email, SENHA, ipAleatorio()))
                .andExpect(jsonPath("$.data.usuario.tema").value("escuro"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/identity/auth/refresh")
                        .cookie(sessao.cookie()))
                .andExpect(jsonPath("$.data.usuario.tema").value("escuro"));
        mvc.perform(get("/api/identity/auth/me").header("Authorization", sessao.autorizacao()))
                .andExpect(jsonPath("$.data.usuario.tema").value("escuro"));
    }

    @Test
    void buscaUsuariosDoProprioTenantPeloNomeOuEmail() throws Exception {
        String token = tokenDe("administrador@empresa-a.dev");
        String email = emailAleatorio("busca");
        criarUsuario(EMPRESA_A, email, "Zuleica Buscável", "VENDEDOR");
        criarUsuario(EMPRESA_B, emailAleatorio("busca-b"), "Zuleica da Outra Empresa", "VENDEDOR");

        mvc.perform(get("/api/identity/busca").param("q", "zuleica").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].titulo").value("Zuleica Buscável"))
                .andExpect(jsonPath("$.data[0].subtitulo").value(email))
                .andExpect(jsonPath("$.data[0].rota").value(org.hamcrest.Matchers.startsWith("/admin/usuarios?usuario=")));
        mvc.perform(get("/api/identity/busca").param("q", email.substring(0, 12)).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data[0].subtitulo").value(email));

        // % não é curinga: sem isso, "%%" traria os cinco primeiros usuários do tenant
        mvc.perform(get("/api/identity/busca").param("q", "%%").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/identity/busca").param("q", "z").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        // Sem identity.usuario.ver, 403 — e a casca omite o identity do resultado
        mvc.perform(get("/api/identity/busca").param("q", "zuleica")
                        .header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void menuDizQuaisModulosTemBusca() throws Exception {
        mvc.perform(get("/api/identity/modulos").header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(jsonPath("$.data[?(@.codigo == 'exemplo')].busca").value(true));
    }
}
