package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Equipes, membros e líderes — Requisito RF57 e decisão D16 (claim "equipes" no token). */
class EquipesTest extends BaseIntegracao {

    private static final String EQUIPES = "/api/identity/equipes";

    @Autowired
    JwtDecoder decodificador;

    private String administrador;

    @BeforeEach
    void entrarComoAdministrador() throws Exception {
        administrador = tokenDe("administrador@empresa-a.dev");
    }

    @Test
    void criarEditarEExcluirComMembrosELideres() throws Exception {
        UUID ana = criarUsuario(EMPRESA_A, emailAleatorio("ana"), "Ana Suporte", "TECNICO");
        UUID bruno = criarUsuario(EMPRESA_A, emailAleatorio("bruno"), "Bruno Suporte", "TECNICO");
        String nome = "Suporte " + UUID.randomUUID();

        // O mesmo membro duas vezes vira um só
        String corpo = mvc.perform(escrita(post(EQUIPES), administrador,
                        equipe(nome, ana, false, bruno, true, ana, false)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.nome").value(nome))
                .andExpect(jsonPath("$.data.membros[*].nome").value(contains("Bruno Suporte", "Ana Suporte")))
                .andExpect(jsonPath("$.data.membros[0].lider").value(true))
                .andExpect(jsonPath("$.data.membros[1].lider").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(corpo, "$.data.id"));

        mvc.perform(escrita(post(EQUIPES), administrador, equipe(nome.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].codigo").value("NOME_EM_USO"));

        mvc.perform(get(EQUIPES).param("busca", nome).header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].totalMembros").value(2))
                .andExpect(jsonPath("$.data.itens[0].lideres").value(contains("Bruno Suporte")));

        mvc.perform(escrita(put(EQUIPES + "/" + id), administrador, equipe(nome + " 2", ana, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value(nome + " 2"))
                .andExpect(jsonPath("$.data.membros.length()").value(1))
                .andExpect(jsonPath("$.data.membros[0].lider").value(true));
        Map<String, Object> auditoria = jdbc.queryForMap("""
                SELECT valor_anterior::text AS anterior, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'alterar' AND entidade = 'equipe_membros' AND entidade_id = ?
                 ORDER BY created_at DESC LIMIT 1
                """, id);
        assertThat((List<String>) JsonPath.read((String) auditoria.get("anterior"), "$.membros"))
                .containsExactly("Ana Suporte", "Bruno Suporte (líder)");
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.membros"))
                .containsExactly("Ana Suporte (líder)");

        mvc.perform(escrita(delete(EQUIPES + "/" + id), administrador, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Equipe excluída."));
        mvc.perform(get(EQUIPES + "/" + id).header("Authorization", bearer(administrador))).andExpect(status().isNotFound());
        mvc.perform(get(EQUIPES + "/" + id + "/membros").header("Authorization", bearer(administrador)))
                .andExpect(status().isNotFound());
    }

    @Test
    void equipeNovaEntraNoTokenNaRenovacaoESaiQuandoExcluida() throws Exception {
        String email = emailAleatorio("novo-no-time");
        UUID usuario = criarUsuario(EMPRESA_A, email, "Novo no time", "VENDEDOR");
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");
        assertThat(decodificador.decode(sessao.token()).getClaimAsStringList("equipes")).isEmpty();

        String corpo = mvc.perform(escrita(post(EQUIPES), administrador, equipe("Time " + UUID.randomUUID(),
                        usuario, false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(corpo, "$.data.id");

        MockHttpServletResponse renovada = renovar(sessao.cookie());
        assertThat(equipesDoToken(renovada)).containsExactly(id);

        mvc.perform(escrita(delete(EQUIPES + "/" + id), administrador, "")).andExpect(status().isOk());
        assertThat(equipesDoToken(renovar(renovada.getCookie("refresh_token")))).isEmpty();
    }

    @Test
    void membroDeOutroTenantComoSeNaoExistisse() throws Exception {
        mvc.perform(escrita(post(EQUIPES), administrador, equipe("Mista " + UUID.randomUUID(),
                        idDoUsuario("vendedor@empresa-b.dev"), false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("membros"))
                .andExpect(jsonPath("$.errors[0].codigo").value("USUARIO_INEXISTENTE"));
    }

    @Test
    void semPermissaoOuDeOutroTenant() throws Exception {
        mvc.perform(escrita(post(EQUIPES), tokenDe("vendedor@empresa-a.dev"), equipe("Do vendedor")))
                .andExpect(status().isForbidden());
        mvc.perform(get(EQUIPES).header("Authorization", bearer(tokenDe("cliente@empresa-a.dev"))))
                .andExpect(status().isForbidden());

        mvc.perform(get(EQUIPES + "/" + EQUIPE_COMERCIAL_B).header("Authorization", bearer(administrador)))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(put(EQUIPES + "/" + EQUIPE_COMERCIAL_B), administrador, equipe("Comercial")))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(delete(EQUIPES + "/" + EQUIPE_COMERCIAL_B), administrador, ""))
                .andExpect(status().isNotFound());
        mvc.perform(get(EQUIPES).param("busca", "Comercial").header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.itens[*].id").value(contains(EQUIPE_COMERCIAL_A)));
    }

    @Test
    void camposInvalidosRespondem400() throws Exception {
        mvc.perform(escrita(post(EQUIPES), administrador, json("nome", texto(" "), "membros", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("nome"));
        mvc.perform(escrita(post(EQUIPES), administrador, json("nome", texto("Sem lista"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("membros"));
    }

    // ------------------------------------------------------------------ apoio

    private MockHttpServletResponse renovar(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/identity/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private List<String> equipesDoToken(MockHttpServletResponse resposta) throws Exception {
        String token = JsonPath.read(resposta.getContentAsString(), "$.data.accessToken");
        return decodificador.decode(token).getClaimAsStringList("equipes");
    }

    private static MockHttpServletRequestBuilder escrita(MockHttpServletRequestBuilder requisicao, String token, String corpo) {
        requisicao.header("Authorization", bearer(token)).header("X-Forwarded-For", ipAleatorio());
        return corpo.isEmpty() ? requisicao : requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    /** @param membrosELider pares (UUID do usuário, é líder) */
    private static String equipe(String nome, Object... membrosELider) {
        String membros = Stream.iterate(0, i -> i < membrosELider.length, i -> i + 2)
                .map(i -> json("usuarioId", texto(membrosELider[i].toString()), "lider", membrosELider[i + 1].toString()))
                .collect(Collectors.joining(",", "[", "]"));
        return json("nome", texto(nome), "membros", membros);
    }
}
