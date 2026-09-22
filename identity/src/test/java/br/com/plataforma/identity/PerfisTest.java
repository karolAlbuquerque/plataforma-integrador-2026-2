package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Perfis, matriz de permissões e catálogo — Requisitos RF20 a RF22 e RF26, com as regras
 * aprovadas: sem escalada de privilégio e a empresa nunca fica sem administrador.
 */
class PerfisTest extends BaseIntegracao {

    private static final String PERFIS = "/api/identity/perfis";
    private static final String PERMISSOES = "/api/identity/permissoes";
    private static final List<String> ADMINISTRACAO = List.of("identity.acessar", "identity.usuario.ver",
            "identity.usuario.administrar", "identity.perfil.ver", "identity.perfil.administrar");

    @Autowired
    JwtDecoder decodificador;

    private String administrador;

    @BeforeEach
    void entrarComoAdministrador() throws Exception {
        administrador = tokenDe("administrador@empresa-a.dev");
    }

    // ------------------------------------------------------------------ leitura

    @Test
    void catalogoAgrupadoPorModuloComAPlataformaPrimeiro() throws Exception {
        mvc.perform(get(PERMISSOES).header("Authorization", bearer(tokenDe("gestor@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modulo").value("identity"))
                .andExpect(jsonPath("$.data[0].nome").value("Plataforma"))
                .andExpect(jsonPath("$.data[0].permissoes[*].codigo", hasItem("identity.perfil.administrar")))
                .andExpect(jsonPath("$.data[?(@.modulo == 'exemplo')].nome").value(contains("Exemplo")))
                .andExpect(jsonPath("$.data[*].permissoes[*].codigo", not(hasItem("crm.empresa.ver"))));

        mvc.perform(get(PERMISSOES).header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void osDezPerfisDeSistemaVemPrimeiroComORotulo() throws Exception {
        mvc.perform(get(PERFIS).header("Authorization", bearer(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itens[0].nome").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.data.itens[0].rotulo").value("Administrador"))
                .andExpect(jsonPath("$.data.itens[0].sistema").value(true))
                .andExpect(jsonPath("$.data.itens[7].rotulo").value("Pré-vendas"))
                .andExpect(jsonPath("$.data.itens[9].sistema").value(true));
    }

    @Test
    void quemSoAdministraUsuariosListaPerfisMasNaoAbreAMatriz() throws Exception {
        String nome = "Só usuários " + UUID.randomUUID();
        criarPerfil(EMPRESA_A, nome, List.of("identity.usuario.administrar"));
        String email = emailAleatorio("so-usuarios");
        criarUsuario(EMPRESA_A, email, "Só administra usuários", nome);
        String token = tokenDe(email);

        mvc.perform(get(PERFIS).header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get(PERFIS + "/" + perfilChamado(EMPRESA_A, "GESTOR")).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ criação e edição

    @Test
    void criarPerfilPersonalizadoAuditaQuemDeuCadaPermissao() throws Exception {
        String nome = "Suporte N1 " + UUID.randomUUID();
        String corpo = mvc.perform(escrita(post(PERFIS), administrador, perfil(nome, "  Atendimento de primeiro nível  ",
                        List.of("identity.timeline.ver", "identity.equipe.ver_resumo", "identity.timeline.ver"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.sistema").value(false))
                .andExpect(jsonPath("$.data.rotulo").value(nome))
                .andExpect(jsonPath("$.data.descricao").value("Atendimento de primeiro nível"))
                .andExpect(jsonPath("$.data.totalUsuarios").value(0))
                .andExpect(jsonPath("$.data.permissoes").value(contains("identity.equipe.ver_resumo", "identity.timeline.ver")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(corpo, "$.data.id"));
        Map<String, Object> auditoria = jdbc.queryForMap("""
                SELECT usuario_id, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'alterar' AND entidade = 'perfil_permissoes' AND entidade_id = ?
                """, id);
        assertThat(auditoria.get("usuario_id")).isEqualTo(idDoUsuario("administrador@empresa-a.dev"));
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.adicionadas"))
                .containsExactly("identity.equipe.ver_resumo", "identity.timeline.ver");

        // Nome único na empresa, sem diferença de maiúsculas — inclusive contra os de sistema
        mvc.perform(escrita(post(PERFIS), administrador, perfil(nome.toUpperCase(), null, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].codigo").value("NOME_EM_USO"));
        mvc.perform(escrita(post(PERFIS), administrador, perfil("administrador", null, List.of())))
                .andExpect(status().isConflict());
    }

    @Test
    void permissaoForaDoCatalogoNaoEntra() throws Exception {
        for (String codigo : List.of("crm.empresa.ver", "identity.nao.existe")) {
            mvc.perform(escrita(post(PERFIS), administrador, perfil("Inventado " + UUID.randomUUID(), null, List.of(codigo))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].campo").value("permissoes"))
                    .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_INEXISTENTE"));
        }
    }

    @Test
    void mudancaNaMatrizFicaNaAuditoriaEValeNaProximaRenovacao() throws Exception {
        String nome = "Matriz " + UUID.randomUUID();
        UUID id = UUID.fromString(JsonPath.read(mvc.perform(escrita(post(PERFIS), administrador,
                        perfil(nome, null, List.of("identity.timeline.ver"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id"));
        String email = emailAleatorio("matriz");
        criarUsuario(EMPRESA_A, email, "Quem usa o perfil", nome);
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");

        mvc.perform(escrita(put(PERFIS + "/" + id), administrador,
                        perfil(nome, null, List.of("identity.equipe.ver_resumo", "identity.usuario.ver"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsuarios").value(1))
                .andExpect(jsonPath("$.data.permissoes").value(contains("identity.equipe.ver_resumo", "identity.usuario.ver")));

        Map<String, Object> auditoria = jdbc.queryForMap("""
                SELECT valor_anterior::text AS anterior, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'alterar' AND entidade = 'perfil_permissoes' AND entidade_id = ?
                 ORDER BY created_at DESC LIMIT 1
                """, id);
        assertThat((List<String>) JsonPath.read((String) auditoria.get("anterior"), "$.permissoes"))
                .containsExactly("identity.timeline.ver");
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.adicionadas"))
                .containsExactly("identity.equipe.ver_resumo", "identity.usuario.ver");
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.removidas"))
                .containsExactly("identity.timeline.ver");

        String renovado = JsonPath.read(mvc.perform(post("/api/identity/auth/refresh").cookie(sessao.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
        assertThat(decodificador.decode(renovado).getClaimAsStringList("perms"))
                .contains("identity.usuario.ver").doesNotContain("identity.timeline.ver");
    }

    @Test
    void duplicarCriaUmaCopiaComumComAsMesmasPermissoes() throws Exception {
        UUID gestor = perfilChamado(EMPRESA_A, "GESTOR");
        List<String> doGestor = JsonPath.read(mvc.perform(get(PERFIS + "/" + gestor).header("Authorization", bearer(administrador)))
                .andReturn().getResponse().getContentAsString(), "$.data.permissoes");
        String nome = "Gestor regional " + UUID.randomUUID();

        String corpo = mvc.perform(escrita(post(PERFIS + "/" + gestor + "/duplicar"), administrador, json("nome", texto(nome))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nome").value(nome))
                .andExpect(jsonPath("$.data.sistema").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(corpo, "$.data.permissoes")).containsExactlyElementsOf(doGestor);

        mvc.perform(escrita(post(PERFIS + "/" + gestor + "/duplicar"), administrador, json("nome", texto(nome))))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ exclusão e perfis de sistema

    @Test
    void perfilDeSistemaNaoMudaDeNomeNemEExcluido() throws Exception {
        UUID administradorId = perfilChamado(EMPRESA_A, "ADMINISTRADOR");
        mvc.perform(escrita(put(PERFIS + "/" + administradorId), administrador, perfil("Chefe", null, List.of("identity.acessar"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("nome"))
                .andExpect(jsonPath("$.errors[0].codigo").value("PERFIL_DE_SISTEMA"));
        mvc.perform(escrita(delete(PERFIS + "/" + administradorId), administrador, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERFIL_DE_SISTEMA"));

        // Nada mudou: a recusa desfaz a transação inteira
        mvc.perform(get(PERFIS + "/" + administradorId).header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.nome").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.data.permissoes", hasItem("identity.usuario.administrar")));
    }

    @Test
    void perfilComUsuarioNaoSaiEOSemUsuarioSaiELiberaONome() throws Exception {
        String emUso = "Em uso " + UUID.randomUUID();
        UUID emUsoId = criarPerfil(EMPRESA_A, emUso, List.of("identity.timeline.ver"));
        criarUsuario(EMPRESA_A, emailAleatorio("usa-o-perfil"), "Quem usa o perfil", emUso);
        mvc.perform(escrita(delete(PERFIS + "/" + emUsoId), administrador, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERFIL_EM_USO"))
                .andExpect(jsonPath("$.message").value("1 usuário ainda tem este perfil. Troque o perfil dele antes de excluir."));

        String livre = "Livre " + UUID.randomUUID();
        UUID livreId = criarPerfil(EMPRESA_A, livre, List.of());
        mvc.perform(escrita(delete(PERFIS + "/" + livreId), administrador, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Perfil excluído."));
        mvc.perform(get(PERFIS + "/" + livreId).header("Authorization", bearer(administrador)))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(post(PERFIS), administrador, perfil(livre, null, List.of())))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ regras de proteção

    @Test
    void quemMontaPerfisNaoConcedeOQueNaoTem() throws Exception {
        String nome = "Editor de perfis " + UUID.randomUUID();
        criarPerfil(EMPRESA_A, nome, List.of("identity.perfil.ver", "identity.perfil.administrar", "identity.timeline.ver"));
        String email = emailAleatorio("editor");
        criarUsuario(EMPRESA_A, email, "Editor de perfis", nome);
        String editor = tokenDe(email);
        UUID gestor = perfilChamado(EMPRESA_A, "GESTOR");

        mvc.perform(escrita(post(PERFIS), editor, perfil("Com exemplo " + UUID.randomUUID(), null, List.of("exemplo.acessar"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("permissoes"))
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));
        mvc.perform(escrita(put(PERFIS + "/" + gestor), editor, perfil("GESTOR", null, List.of("identity.timeline.ver"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("id"))
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));
        mvc.perform(escrita(post(PERFIS + "/" + gestor + "/duplicar"), editor, json("nome", texto("Cópia " + UUID.randomUUID()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));

        mvc.perform(escrita(post(PERFIS), editor, perfil("Só timeline " + UUID.randomUUID(), null, List.of("identity.timeline.ver"))))
                .andExpect(status().isCreated());
    }

    @Test
    void aEmpresaNuncaFicaSemAdministrador() throws Exception {
        String tenant = criarTenant("Isolada");
        UUID administracao = criarPerfil(tenant, "Administração", ADMINISTRACAO);
        String email = emailAleatorio("unico-admin");
        criarUsuario(tenant, email, "Único administrador", "Administração");
        UUID outro = criarUsuario(tenant, emailAleatorio("segundo-admin"), "Segundo administrador", "Administração");
        String token = tokenDe(email);

        // Com outro administrador ativo, desativar o segundo pode
        mvc.perform(escrita(post("/api/identity/usuarios/" + outro + "/desativar"), token, ""))
                .andExpect(status().isOk());

        // Tirar do único perfil de administração uma das duas permissões deixaria a empresa sem ninguém
        List<String> semAdministrarUsuarios = ADMINISTRACAO.stream()
                .filter(codigo -> !codigo.equals("identity.usuario.administrar")).toList();
        mvc.perform(escrita(put(PERFIS + "/" + administracao), token, perfil("Administração", null, semAdministrarUsuarios)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("ULTIMO_ADMINISTRADOR"))
                .andExpect(jsonPath("$.message").value("A empresa ficaria sem ninguém que administre usuários e perfis."));

        mvc.perform(get(PERFIS + "/" + administracao).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.permissoes", hasItem("identity.usuario.administrar")));
    }

    // ------------------------------------------------------------------ permissão e tenant

    @Test
    void semPermissaoOuDeOutroTenant() throws Exception {
        mvc.perform(get(PERFIS).header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isForbidden());
        mvc.perform(escrita(post(PERFIS), tokenDe("gestor@empresa-a.dev"), perfil("Do gestor", null, List.of())))
                .andExpect(status().isForbidden());

        UUID daEmpresaB = perfilChamado(EMPRESA_B, "TECNICO");
        mvc.perform(get(PERFIS + "/" + daEmpresaB).header("Authorization", bearer(administrador)))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(put(PERFIS + "/" + daEmpresaB), administrador, perfil("TECNICO", null, List.of())))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(delete(PERFIS + "/" + daEmpresaB), administrador, ""))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(post(PERFIS + "/" + daEmpresaB + "/duplicar"), administrador, json("nome", texto("Cópia da B"))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ apoio

    private static MockHttpServletRequestBuilder escrita(MockHttpServletRequestBuilder requisicao, String token, String corpo) {
        requisicao.header("Authorization", bearer(token)).header("X-Forwarded-For", ipAleatorio());
        return corpo.isEmpty() ? requisicao : requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String perfil(String nome, String descricao, List<String> permissoes) {
        return json("nome", texto(nome), "descricao", texto(descricao),
                "permissoes", permissoes.stream().map(BaseIntegracao::texto).collect(Collectors.joining(",", "[", "]")));
    }
}
