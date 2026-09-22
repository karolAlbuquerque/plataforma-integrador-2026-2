package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Sessões ativas do próprio usuário — Requisito RF06. */
class SessoesTest extends BaseIntegracao {

    private static final String SESSOES = "/api/identity/auth/sessoes";
    private static final String REFRESH = "/api/identity/auth/refresh";
    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36";
    private static final String FIREFOX = "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0";

    @Test
    void cadaDispositivoEUmaSessaoEOAtualVemMarcado() throws Exception {
        String email = emailAleatorio("dois-aparelhos");
        criarUsuario(EMPRESA_A, email, "Quem entra de dois lugares", "TECNICO");
        Sessao aqui = entrar(email, SENHA, CHROME);
        entrar(email, SENHA, FIREFOX);

        String corpo = mvc.perform(get(SESSOES).header("Authorization", aqui.autorizacao()).cookie(aqui.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        List<String> navegadores = JsonPath.read(corpo, "$.data[*].navegador");
        assertThat(navegadores).containsExactlyInAnyOrder("Chrome no Windows", "Firefox no Linux");
        assertThat((List<Boolean>) JsonPath.read(corpo, "$.data[*].atual")).containsExactlyInAnyOrder(true, false);
        assertThat((String) JsonPath.read(corpo, "$.data[0].ip")).isNotBlank();
    }

    @Test
    void renovarContinuaAMesmaSessao() throws Exception {
        String email = emailAleatorio("renova");
        criarUsuario(EMPRESA_A, email, "Quem renova o token", "TECNICO");
        Sessao inicial = entrar(email, SENHA, CHROME);
        String antes = mvc.perform(get(SESSOES).header("Authorization", inicial.autorizacao()).cookie(inicial.cookie()))
                .andReturn().getResponse().getContentAsString();

        MockHttpServletResponse renovada = mvc.perform(post(REFRESH).cookie(inicial.cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String token = JsonPath.read(renovada.getContentAsString(), "$.data.accessToken");
        Cookie novoCookie = renovada.getCookie("refresh_token");

        String id = JsonPath.read(antes, "$.data[0].id");
        String iniciadaEm = JsonPath.read(antes, "$.data[0].iniciadaEm");
        String expiraEm = JsonPath.read(antes, "$.data[0].expiraEm");
        mvc.perform(get(SESSOES).header("Authorization", bearer(token)).cookie(novoCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].iniciadaEm").value(iniciadaEm))
                .andExpect(jsonPath("$.data[0].expiraEm").value(expiraEm))
                .andExpect(jsonPath("$.data[0].atual").value(true));
    }

    @Test
    void encerrarUmaSessaoDerrubaSoOAparelhoEscolhido() throws Exception {
        String email = emailAleatorio("encerra-uma");
        criarUsuario(EMPRESA_A, email, "Quem encerra uma sessão", "TECNICO");
        Sessao aqui = entrar(email, SENHA, CHROME);
        Sessao outra = entrar(email, SENHA, FIREFOX);
        UUID idDaOutra = sessaoNaoAtual(aqui);

        mvc.perform(delete(SESSOES + "/" + idDaOutra).header("Authorization", aqui.autorizacao()).cookie(aqui.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sessão encerrada."));

        mvc.perform(post(REFRESH).cookie(outra.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post(REFRESH).cookie(aqui.cookie())).andExpect(status().isOk());

        // Encerrar de novo, ou encerrar o que não existe, responde 404
        mvc.perform(delete(SESSOES + "/" + idDaOutra).header("Authorization", aqui.autorizacao()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(SESSOES + "/" + UUID.randomUUID()).header("Authorization", aqui.autorizacao()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessaoDeOutroUsuarioResponde404() throws Exception {
        String email = emailAleatorio("curioso");
        criarUsuario(EMPRESA_A, email, "Quem tenta encerrar sessão alheia", "TECNICO");
        Sessao curioso = entrar(email, SENHA, CHROME);

        String outroEmail = emailAleatorio("alvo");
        criarUsuario(EMPRESA_A, outroEmail, "Alvo", "TECNICO");
        Sessao alvo = entrar(outroEmail, SENHA, FIREFOX);
        UUID idDoAlvo = UUID.fromString(JsonPath.read(mvc.perform(get(SESSOES)
                        .header("Authorization", alvo.autorizacao()).cookie(alvo.cookie()))
                .andReturn().getResponse().getContentAsString(), "$.data[0].id"));

        mvc.perform(delete(SESSOES + "/" + idDoAlvo).header("Authorization", curioso.autorizacao()).cookie(curioso.cookie()))
                .andExpect(status().isNotFound());
        mvc.perform(post(REFRESH).cookie(alvo.cookie())).andExpect(status().isOk());
    }

    @Test
    void encerrarAsOutrasMantemODispositivoAtual() throws Exception {
        String email = emailAleatorio("encerra-outras");
        criarUsuario(EMPRESA_A, email, "Quem encerra as outras", "TECNICO");
        Sessao aqui = entrar(email, SENHA, CHROME);
        Sessao segunda = entrar(email, SENHA, FIREFOX);
        Sessao terceira = entrar(email, SENHA, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/605.1.15");

        mvc.perform(delete(SESSOES).header("Authorization", aqui.autorizacao()).cookie(aqui.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2 sessões encerradas."));

        mvc.perform(post(REFRESH).cookie(segunda.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post(REFRESH).cookie(terceira.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post(REFRESH).cookie(aqui.cookie())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs WHERE acao = 'encerrar_outras_sessoes'
                   AND (valor_novo->>'sessoesEncerradas')::int = 2
                """, Integer.class)).isPositive();
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get(SESSOES)).andExpect(status().isUnauthorized());
        mvc.perform(delete(SESSOES)).andExpect(status().isUnauthorized());
    }

    private UUID sessaoNaoAtual(Sessao atual) throws Exception {
        String corpo = mvc.perform(get(SESSOES).header("Authorization", atual.autorizacao()).cookie(atual.cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(corpo, "$.data[?(@.atual == false)].id");
        assertThat(ids).hasSize(1);
        return UUID.fromString(ids.get(0));
    }
}
