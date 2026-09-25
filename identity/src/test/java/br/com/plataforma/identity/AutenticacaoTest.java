package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Login, sessão e token de serviço — Requisitos RF01 a RF05 e RF12. */
class AutenticacaoTest extends BaseIntegracao {

    private static final String REFRESH = "/api/identity/auth/refresh";
    private static final String MENSAGEM_401 = "E-mail ou senha incorretos.";

    @Autowired
    JwtDecoder decodificador;

    @Test
    void loginDevolveTokenAssinadoComOsClaimsDoContrato() throws Exception {
        MockHttpServletResponse resposta = mvc.perform(login("administrador@empresa-a.dev", SENHA, ipAleatorio()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expiraEmSegundos").value(900))
                .andExpect(jsonPath("$.data.usuario.email").value("administrador@empresa-a.dev"))
                .andExpect(jsonPath("$.data.usuario.tenantId").value(EMPRESA_A))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String cookie = resposta.getHeader("Set-Cookie");
        assertThat(cookie).startsWith("refresh_token=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/identity/auth", "Max-Age=28800");

        Jwt jwt = decodificador.decode(JsonPath.read(resposta.getContentAsString(), "$.data.accessToken"));
        assertThat(jwt.getHeaders()).containsEntry("kid", "dev-2026").containsEntry("alg", "RS256");
        assertThat(jwt.getIssuer()).hasToString("http://identity:8081");
        assertThat(jwt.getAudience()).containsExactly("plataforma");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(EMPRESA_A);
        assertThat(jwt.getClaimAsString("nome")).isEqualTo("Administrador da Empresa A");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMINISTRADOR");
        assertThat(jwt.getClaimAsStringList("perms"))
                .contains("identity.usuario.administrar", "exemplo.acessar", "exemplo.item.criar")
                .doesNotContain("crm.empresa.ver");   // declarada na lista errada: ignorada
        assertThat(jwt.getClaimAsStringList("equipes")).isEmpty();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
    }

    @Test
    void vendedorRecebeAEquipeNoToken() throws Exception {
        Jwt jwt = decodificador.decode(tokenDe("vendedor@empresa-a.dev"));
        assertThat(jwt.getClaimAsStringList("equipes")).containsExactly(EQUIPE_COMERCIAL_A);
        assertThat(jwt.getClaimAsStringList("perms")).contains("identity.equipe.ver_resumo")
                .doesNotContain("exemplo.acessar");
    }

    @Test
    void senhaErradaEEmailInexistenteTemAMesmaResposta() throws Exception {
        String ip = ipAleatorio();
        mvc.perform(login("gestor@empresa-b.dev", "senha-errada", ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(MENSAGEM_401));
        mvc.perform(login("ninguem@empresa-b.dev", SENHA, ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(MENSAGEM_401))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void usuarioDesativadoNaoEntra() throws Exception {
        jdbc.update("UPDATE identity.usuarios SET ativo = false WHERE email = 'parceiro@empresa-b.dev'");
        mvc.perform(login("parceiro@empresa-b.dev", SENHA, ipAleatorio()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(MENSAGEM_401));
    }

    @Test
    void sextaTentativaComOMesmoEmailResponde429MesmoComASenhaCerta() throws Exception {
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            mvc.perform(login("contabilidade@empresa-b.dev", "errada-" + tentativa, ipAleatorio()))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("contabilidade@empresa-b.dev", SENHA, ipAleatorio()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Aguarde 15 minutos")));
    }

    @Test
    void sextaTentativaDoMesmoIpResponde429ParaQualquerEmail() throws Exception {
        String ip = ipAleatorio();
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            mvc.perform(login("inexistente-" + tentativa + "@empresa-a.dev", SENHA, ip))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("tecnico@empresa-a.dev", SENHA, ip))
                .andExpect(status().isTooManyRequests());
        // O IP que conta é o último do X-Forwarded-For, o que o gateway acrescenta
        mvc.perform(login("tecnico@empresa-a.dev", SENHA, ip + ", " + ipAleatorio()))
                .andExpect(status().isOk());
    }

    @Test
    void campoFaltandoResponde400ComOCampo() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("email"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_OBRIGATORIO"));
    }

    @Test
    void jwksPublicaSoAChavePublica() throws Exception {
        mvc.perform(get("/api/identity/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("dev-2026"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void tokenDeServicoTemSubSvcESemTenant() throws Exception {
        String corpo = mvc.perform(post("/api/identity/auth/token-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"landing\",\"clientSecret\":\"segredo-do-landing-para-teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = decodificador.decode(JsonPath.read(corpo, "$.data.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo("svc:landing");
        assertThat(jwt.hasClaim("tenant_id")).isFalse();
        // servicos: [landing] no catálogo — a do exemplo e o resolver de tenant da superfície pública
        assertThat(jwt.getClaimAsStringList("perms")).containsExactlyInAnyOrder("exemplo.item.criar", "identity.tenant.ver");

        mvc.perform(post("/api/identity/auth/token-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"landing\",\"clientSecret\":\"segredo-errado\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/identity/auth/token-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"crm\",\"clientSecret\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized());   // sem SVC_CRM_SEGREDO, não há credencial
    }

    @Test
    void refreshRotacionaOCookieEOAntigoDeixaDeValer() throws Exception {
        MockHttpServletResponse login = mvc.perform(login("gestor@empresa-a.dev", SENHA, ipAleatorio()))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie primeiro = login.getCookie("refresh_token");
        Instant fimDaSessao = Instant.parse(JsonPath.read(login.getContentAsString(), "$.data.sessaoExpiraEm"));
        assertThat(fimDaSessao).isBetween(Instant.now().plus(Duration.ofMinutes(479)), Instant.now().plus(Duration.ofHours(8)));

        MockHttpServletResponse renovada = mvc.perform(post(REFRESH).cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.usuario.email").value("gestor@empresa-a.dev"))
                .andReturn().getResponse();
        Cookie segundo = renovada.getCookie("refresh_token");
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());
        // A rotação não estende a sessão além das 8 horas do login — e a casca sabe quando ela acaba (RF41)
        assertThat(segundo.getMaxAge()).isLessThanOrEqualTo(primeiro.getMaxAge());
        assertThat(Instant.parse(JsonPath.read(renovada.getContentAsString(), "$.data.sessaoExpiraEm")))
                .isEqualTo(fimDaSessao);

        mvc.perform(post(REFRESH).cookie(primeiro))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        mvc.perform(post(REFRESH).cookie(segundo)).andExpect(status().isOk());
    }

    @Test
    void refreshSemCookieResponde401() throws Exception {
        mvc.perform(post(REFRESH)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaASessao() throws Exception {
        Cookie cookie = mvc.perform(login("marketing@empresa-a.dev", SENHA, ipAleatorio()))
                .andReturn().getResponse().getCookie("refresh_token");

        mvc.perform(post("/api/identity/auth/logout").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post(REFRESH).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void meDevolveUsuarioTenantPerfisEEquipes() throws Exception {
        mvc.perform(get("/api/identity/auth/me").header("Authorization", bearer(tokenDe("pre-vendas@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.nome").value("Pré-vendas da Empresa A"))
                .andExpect(jsonPath("$.data.tenant.id").value(EMPRESA_A))
                .andExpect(jsonPath("$.data.tenant.nome").value("Empresa A"))
                .andExpect(jsonPath("$.data.perfis").value(contains("PRE_VENDAS")))
                .andExpect(jsonPath("$.data.permissoes", hasItem("identity.timeline.ver")))
                .andExpect(jsonPath("$.data.permissoes", not(hasItem("identity.usuario.ver"))))
                .andExpect(jsonPath("$.data.equipes").value(contains(EQUIPE_COMERCIAL_A)));
    }

    @Test
    void semTokenResponde401NoEnvelope() throws Exception {
        mvc.perform(get("/api/identity/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Autenticação necessária."));
        mvc.perform(get("/api/identity/auth/me").header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void falhaDeLoginEAuditadaSemASenha() throws Exception {
        mvc.perform(login("ninguem-auditado@empresa-a.dev", "senha-que-nao-pode-vazar", ipAleatorio()))
                .andExpect(status().isUnauthorized());
        String detalhe = jdbc.queryForObject("""
                SELECT valor_novo::text FROM identity.audit_logs
                 WHERE acao = 'login_falhou' AND valor_novo->>'email' = 'ninguem-auditado@empresa-a.dev'
                """, String.class);
        assertThat(detalhe).contains("email_inexistente").doesNotContain("senha-que-nao-pode-vazar");
    }
}
