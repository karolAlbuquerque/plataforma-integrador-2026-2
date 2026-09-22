package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Administração de usuários — Requisitos RF13 a RF17, RF23 e as regras aprovadas em 21/09/2026:
 * ninguém desativa a si mesmo nem muda os próprios perfis, e ninguém concede o que não tem.
 */
class UsuariosTest extends BaseIntegracao {

    private static final String USUARIOS = "/api/identity/usuarios";
    private static final String REFRESH = "/api/identity/auth/refresh";

    @Autowired
    JwtDecoder decodificador;

    private String administrador;

    @BeforeEach
    void entrarComoAdministrador() throws Exception {
        administrador = tokenDe("administrador@empresa-a.dev");
    }

    // ------------------------------------------------------------------ cadastro e convite

    @Test
    void cadastroCriaSemSenhaEConvidaPorEmail() throws Exception {
        String email = emailAleatorio("Maria.Souza");
        UUID equipe = criarEquipe(EMPRESA_A, "Pós-venda " + UUID.randomUUID());

        MockHttpServletResponse resposta = mvc.perform(escrita(post(USUARIOS), administrador,
                        cadastro("  Maria Souza  ", email.toUpperCase(), "(81) 98888-7777",
                                List.of(perfilChamado(EMPRESA_A, "VENDEDOR"), perfilChamado(EMPRESA_A, "PRE_VENDAS")),
                                List.of(equipe))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/identity/usuarios/")))
                .andExpect(jsonPath("$.data.conviteEnviado").value(true))
                .andExpect(jsonPath("$.data.usuario.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.data.usuario.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.usuario.situacao").value("convite_pendente"))
                .andExpect(jsonPath("$.data.usuario.conviteExpiraEm").isNotEmpty())
                .andExpect(jsonPath("$.data.usuario.perfis[*].rotulo").value(contains("Pré-vendas", "Vendedor")))
                .andExpect(jsonPath("$.data.usuario.equipes[0].id").value(equipe.toString()))
                .andReturn().getResponse();

        UUID id = UUID.fromString(JsonPath.read(resposta.getContentAsString(), "$.data.usuario.id"));
        assertThat(resposta.getHeader("Location")).endsWith(id.toString());
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM identity.usuarios WHERE id = ?", String.class, id)).isNull();
        assertThat(emailsPara(email.toLowerCase())).hasSize(1);

        // Quem criou e quem deu cada perfil fica na auditoria (RF26)
        Map<String, Object> perfis = jdbc.queryForMap("""
                SELECT usuario_id, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'alterar' AND entidade = 'usuario_perfis' AND entidade_id = ?
                """, id);
        assertThat(perfis.get("usuario_id")).isEqualTo(idDoUsuario("administrador@empresa-a.dev"));
        assertThat((List<String>) JsonPath.read((String) perfis.get("novo"), "$.adicionados"))
                .containsExactlyInAnyOrder("VENDEDOR", "PRE_VENDAS");
    }

    @Test
    void emailJaUsadoEmQualquerTenantResponde409() throws Exception {
        for (String email : List.of("vendedor@empresa-b.dev", "VENDEDOR@Empresa-A.dev")) {
            mvc.perform(escrita(post(USUARIOS), administrador, cadastro("Repetido", email, null,
                            List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].campo").value("email"))
                    .andExpect(jsonPath("$.errors[0].codigo").value("EMAIL_EM_USO"));
        }
    }

    @Test
    void perfilOuEquipeDeOutroTenantComoSeNaoExistisse() throws Exception {
        mvc.perform(escrita(post(USUARIOS), administrador, cadastro("Invasor", emailAleatorio("invasor"), null,
                        List.of(perfilChamado(EMPRESA_B, "ADMINISTRADOR")), List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERFIL_INEXISTENTE"));
        mvc.perform(escrita(post(USUARIOS), administrador, cadastro("Invasor", emailAleatorio("invasor"), null,
                        List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of(UUID.fromString(EQUIPE_COMERCIAL_B)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("EQUIPE_INEXISTENTE"));
    }

    @Test
    void camposInvalidosRespondem400() throws Exception {
        mvc.perform(escrita(post(USUARIOS), administrador, cadastro("Sem perfil", emailAleatorio("sem-perfil"), null,
                        List.of(), List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("perfis"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_OBRIGATORIO"));
        mvc.perform(escrita(post(USUARIOS), administrador, cadastro("E-mail ruim", "sem-arroba", null,
                        List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("email"));
    }

    @Test
    void trocarOEmailDeQuemNaoDefiniuSenhaConvidaONovoEndereco() throws Exception {
        String antigo = emailAleatorio("antigo");
        UUID id = cadastrar("Troca de e-mail", antigo, "TECNICO");
        String primeiroToken = tokenDoEmailPara(antigo, "Seu acesso");

        String novo = emailAleatorio("novo");
        mvc.perform(escrita(put(USUARIOS + "/" + id), administrador, cadastro("Troca de e-mail", novo, null,
                        List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.email").value(novo))
                .andExpect(jsonPath("$.data.conviteEnviado").value(true));

        tokenDoEmailPara(novo, "Seu acesso");
        mvc.perform(post("/api/identity/auth/senha/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", texto(primeiroToken))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ desativar e reativar (RF16)

    @Test
    void desativarBloqueiaNaHoraEEncerraAsSessoes() throws Exception {
        String email = emailAleatorio("sai-da-empresa");
        UUID id = criarUsuario(EMPRESA_A, email, "Quem sai da empresa", "TECNICO");
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");

        mvc.perform(escrita(post(USUARIOS + "/" + id + "/desativar"), administrador, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.situacao").value("inativo"))
                .andExpect(jsonPath("$.data.conviteEnviado").doesNotExist());

        mvc.perform(post(REFRESH).cookie(sessao.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT (valor_novo->>'sessoesEncerradas')::int FROM identity.audit_logs
                 WHERE acao = 'desativar' AND entidade_id = ?
                """, Integer.class, id)).isEqualTo(1);

        // Quem já tinha senha volta a entrar com ela, sem convite
        mvc.perform(escrita(post(USUARIOS + "/" + id + "/reativar"), administrador, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.situacao").value("ativo"))
                .andExpect(jsonPath("$.data.conviteEnviado").doesNotExist());
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isOk());
    }

    @Test
    void desativarCancelaOConviteEReativarMandaOutro() throws Exception {
        String email = emailAleatorio("convite-cancelado");
        UUID id = cadastrar("Convite cancelado", email, "TECNICO");
        String token = tokenDoEmailPara(email, "Seu acesso");

        mvc.perform(escrita(post(USUARIOS + "/" + id + "/desativar"), administrador, "")).andExpect(status().isOk());
        mvc.perform(post("/api/identity/auth/senha/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", texto(token))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(escrita(post(USUARIOS + "/" + id + "/convite"), administrador, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("USUARIO_INATIVO"));

        mvc.perform(escrita(post(USUARIOS + "/" + id + "/reativar"), administrador, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conviteEnviado").value(true))
                .andExpect(jsonPath("$.data.usuario.situacao").value("convite_pendente"));
        assertThat(emailsPara(email)).hasSize(2);
    }

    @Test
    void conviteParaQuemJaTemSenhaResponde422() throws Exception {
        UUID id = criarUsuario(EMPRESA_A, emailAleatorio("ja-tem-senha"), "Já tem senha", "TECNICO");
        mvc.perform(escrita(post(USUARIOS + "/" + id + "/convite"), administrador, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("CONVITE_DESNECESSARIO"));
    }

    // ------------------------------------------------------------------ regras de proteção

    @Test
    void ninguemSeDesativaNemMudaOsPropriosPerfis() throws Exception {
        String email = emailAleatorio("admin-proprio");
        UUID eu = criarUsuario(EMPRESA_A, email, "Administrador de si mesmo", "ADMINISTRADOR");
        String token = tokenDe(email);

        mvc.perform(escrita(post(USUARIOS + "/" + eu + "/desativar"), token, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("AUTOPROTECAO"));
        mvc.perform(escrita(put(USUARIOS + "/" + eu), token, cadastro("Administrador de si mesmo", email, null,
                        List.of(perfilChamado(EMPRESA_A, "ADMINISTRADOR"), perfilChamado(EMPRESA_A, "GESTOR")), List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("perfis"))
                .andExpect(jsonPath("$.errors[0].codigo").value("AUTOPROTECAO"));

        // Os próprios dados, com os mesmos perfis, pode
        mvc.perform(escrita(put(USUARIOS + "/" + eu), token, cadastro("Nome corrigido", email, "(81) 3333-4444",
                        List.of(perfilChamado(EMPRESA_A, "ADMINISTRADOR")), List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.nome").value("Nome corrigido"));
    }

    @Test
    void quemAdministraUsuariosNaoConcedeOQueNaoTem() throws Exception {
        String nomeDoPerfil = "Gerente de acesso " + UUID.randomUUID();
        UUID gerencia = criarPerfil(EMPRESA_A, nomeDoPerfil, List.of("identity.acessar", "identity.usuario.ver",
                "identity.usuario.administrar"));
        String email = emailAleatorio("gerente");
        criarUsuario(EMPRESA_A, email, "Gerente de acesso", nomeDoPerfil);
        String gerente = tokenDe(email);
        UUID administradorA = idDoUsuario("administrador@empresa-a.dev");

        mvc.perform(escrita(post(USUARIOS), gerente, cadastro("Novo chefe", emailAleatorio("chefe"), null,
                        List.of(perfilChamado(EMPRESA_A, "ADMINISTRADOR")), List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("perfis"))
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));
        mvc.perform(escrita(put(USUARIOS + "/" + administradorA), gerente, cadastro("Rebaixado", "administrador@empresa-a.dev",
                        null, List.of(gerencia), List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));
        mvc.perform(escrita(post(USUARIOS + "/" + administradorA + "/desativar"), gerente, ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERMISSAO_NAO_CONCEDIVEL"));

        // O que ele tem, ele concede
        mvc.perform(escrita(post(USUARIOS), gerente, cadastro("Colega", emailAleatorio("colega"), null,
                        List.of(gerencia), List.of())))
                .andExpect(status().isCreated());
        mvc.perform(get(USUARIOS + "/" + administradorA).header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.nome").value("Administrador da Empresa A"))
                .andExpect(jsonPath("$.data.situacao").value("ativo"));
    }

    // ------------------------------------------------------------------ permissão e tenant

    @Test
    void semTokenSemPermissaoEOutroTenant() throws Exception {
        mvc.perform(get(USUARIOS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USUARIOS).header("Authorization", bearer(tokenDe("vendedor@empresa-a.dev"))))
                .andExpect(status().isForbidden());

        // O gestor vê, mas não muda
        String gestor = tokenDe("gestor@empresa-a.dev");
        mvc.perform(get(USUARIOS).header("Authorization", bearer(gestor))).andExpect(status().isOk());
        mvc.perform(escrita(post(USUARIOS), gestor, cadastro("Não pode", emailAleatorio("nao-pode"), null,
                        List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of())))
                .andExpect(status().isForbidden());

        UUID daEmpresaB = idDoUsuario("marketing@empresa-b.dev");
        mvc.perform(get(USUARIOS + "/" + daEmpresaB).header("Authorization", bearer(administrador)))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(put(USUARIOS + "/" + daEmpresaB), administrador, cadastro("Invasão", "marketing@empresa-b.dev",
                        null, List.of(perfilChamado(EMPRESA_A, "TECNICO")), List.of())))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(post(USUARIOS + "/" + daEmpresaB + "/desativar"), administrador, ""))
                .andExpect(status().isNotFound());
        mvc.perform(escrita(post(USUARIOS + "/" + daEmpresaB + "/convite"), administrador, ""))
                .andExpect(status().isNotFound());
        mvc.perform(get(USUARIOS + "/" + daEmpresaB).header("Authorization", bearer(tokenDe("administrador@empresa-b.dev"))))
                .andExpect(status().isOk());
    }

    @Test
    void listagemSoDoTenantComBuscaFiltroSituacaoEOrdem() throws Exception {
        String marca = "Zeta" + UUID.randomUUID().toString().substring(0, 6);
        criarUsuario(EMPRESA_A, emailAleatorio("zeta-ativo"), marca + " Ativo", "VENDEDOR");
        criarUsuarioSemSenha(EMPRESA_A, emailAleatorio("zeta-sem-convite"), marca + " Sem convite", "TECNICO");
        criarUsuario(EMPRESA_B, emailAleatorio("zeta-da-b"), marca + " da Empresa B", "VENDEDOR");

        mvc.perform(get(USUARIOS).param("busca", marca.toLowerCase()).header("Authorization", bearer(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.itens[*].nome").value(contains(marca + " Ativo", marca + " Sem convite")));
        mvc.perform(get(USUARIOS).param("busca", marca).param("ordenar", "nome,desc")
                        .header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.itens[0].nome").value(marca + " Sem convite"));
        mvc.perform(get(USUARIOS).param("busca", marca).param("situacao", "convite_expirado")
                        .header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].situacao").value("convite_expirado"));
        mvc.perform(get(USUARIOS).param("busca", marca).param("perfilId", perfilChamado(EMPRESA_A, "VENDEDOR").toString())
                        .header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].perfis[0].rotulo").value("Vendedor"));

        // Curinga do LIKE é texto, não curinga; e nada da Empresa B aparece
        mvc.perform(get(USUARIOS).param("busca", "%").header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(USUARIOS).param("busca", "empresa-b").header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(USUARIOS).param("tamanho", "1000").header("Authorization", bearer(administrador)))
                .andExpect(jsonPath("$.data.tamanho").value(100));

        mvc.perform(get(USUARIOS).param("situacao", "banido").header("Authorization", bearer(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("situacao"));
        mvc.perform(get(USUARIOS).param("ordenar", "senha_hash").header("Authorization", bearer(administrador)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("ordenar"));
    }

    // ------------------------------------------------------------------ efeito no token

    @Test
    void perfilNovoValeNaProximaRenovacao() throws Exception {
        String email = emailAleatorio("promovido");
        UUID id = criarUsuario(EMPRESA_A, email, "Promovido", "TECNICO");
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");
        assertThat(decodificador.decode(sessao.token()).getClaimAsStringList("perms")).doesNotContain("exemplo.acessar");

        mvc.perform(escrita(put(USUARIOS + "/" + id), administrador, cadastro("Promovido", email, null,
                        List.of(perfilChamado(EMPRESA_A, "TECNICO"), perfilChamado(EMPRESA_A, "GESTOR")), List.of())))
                .andExpect(status().isOk());

        // RF15: a renovação relê os acessos do banco
        String renovado = JsonPath.read(mvc.perform(post(REFRESH).cookie(sessao.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
        Jwt jwt = decodificador.decode(renovado);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("GESTOR", "TECNICO");
        assertThat(jwt.getClaimAsStringList("perms")).contains("exemplo.acessar", "identity.usuario.ver");

        Map<String, Object> auditoria = jdbc.queryForMap("""
                SELECT usuario_id, valor_anterior::text AS anterior, valor_novo::text AS novo FROM identity.audit_logs
                 WHERE acao = 'alterar' AND entidade = 'usuario_perfis' AND entidade_id = ?
                """, id);
        assertThat(auditoria.get("usuario_id")).isEqualTo(idDoUsuario("administrador@empresa-a.dev"));
        assertThat((List<String>) JsonPath.read((String) auditoria.get("anterior"), "$.perfis")).containsExactly("TECNICO");
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.adicionados")).containsExactly("GESTOR");
        assertThat((List<String>) JsonPath.read((String) auditoria.get("novo"), "$.removidos")).isEmpty();
    }

    @Test
    void permissoesDeVariosPerfisSemRepeticao() throws Exception {
        String email = emailAleatorio("varios-perfis");
        criarUsuario(EMPRESA_A, email, "Vários perfis", "VENDEDOR", "PRE_VENDAS", "GESTOR");
        Jwt jwt = decodificador.decode(tokenDe(email));
        List<String> permissoes = jwt.getClaimAsStringList("perms");

        // RF23: identity.timeline.ver vem dos três perfis e aparece uma vez só
        assertThat(permissoes).doesNotHaveDuplicates().contains("identity.timeline.ver", "exemplo.acessar");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("GESTOR", "PRE_VENDAS", "VENDEDOR");
    }

    // ------------------------------------------------------------------ apoio

    private UUID cadastrar(String nome, String email, String perfil) throws Exception {
        String corpo = mvc.perform(escrita(post(USUARIOS), administrador, cadastro(nome, email, null,
                        List.of(perfilChamado(EMPRESA_A, perfil)), List.of())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.data.usuario.id"));
    }

    private static MockHttpServletRequestBuilder escrita(MockHttpServletRequestBuilder requisicao, String token, String corpo) {
        requisicao.header("Authorization", bearer(token)).header("X-Forwarded-For", ipAleatorio());
        return corpo.isEmpty() ? requisicao : requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String cadastro(String nome, String email, String telefone, List<UUID> perfis, List<UUID> equipes) {
        return json("nome", texto(nome), "email", texto(email), "telefone", texto(telefone),
                "perfis", lista(perfis), "equipes", lista(equipes));
    }

    private static String lista(List<UUID> ids) {
        return ids.stream().map(id -> texto(id.toString())).collect(Collectors.joining(",", "[", "]"));
    }
}
