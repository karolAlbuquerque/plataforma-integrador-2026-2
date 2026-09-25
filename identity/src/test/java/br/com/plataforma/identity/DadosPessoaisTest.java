package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.http.MediaType;

import br.com.plataforma.identity.mensageria.Mensagem;
import br.com.plataforma.identity.mensageria.PublicadorDeEventos.DadosUsuarioAnonimizado;

/** Exportação e anonimização dos dados pessoais (Requisito RF56, LGPD art. 18). */
class DadosPessoaisTest extends BaseIntegracao {

    @Test
    void usuarioBaixaOsProprios() throws Exception {
        String email = emailAleatorio("meus-dados");
        UUID id = criarUsuario(EMPRESA_A, email, "Dona dos Dados", "VENDEDOR");
        String token = tokenDe(email);

        mvc.perform(get("/api/identity/conta/dados-pessoais").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cadastro.id").value(id.toString()))
                .andExpect(jsonPath("$.data.cadastro.email").value(email))
                .andExpect(jsonPath("$.data.perfis[0]").value("VENDEDOR"))
                .andExpect(jsonPath("$.data.sessoes.length()").value(1))
                .andExpect(jsonPath("$.data.tentativasDeLogin[0].sucesso").value(true))
                .andExpect(jsonPath("$.data.acoes[?(@.acao == 'login')]").isNotEmpty());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs WHERE acao = 'exportar_dados_pessoais' AND entidade_id = ?
                """, Integer.class, id)).isEqualTo(1);
    }

    @Test
    void soQuemTemAPermissaoExportaOsDeOutro() throws Exception {
        UUID id = criarUsuario(EMPRESA_A, emailAleatorio("dados-alheios"), "Titular", "VENDEDOR");
        mvc.perform(get("/api/identity/usuarios/" + id + "/dados-pessoais")
                        .header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cadastro.nome").value("Titular"));
        // GESTOR vê usuários, mas não exporta dados pessoais
        mvc.perform(get("/api/identity/usuarios/" + id + "/dados-pessoais")
                        .header("Authorization", bearer(tokenDe("gestor@empresa-a.dev"))))
                .andExpect(status().isForbidden());
        // De outro tenant: 404, como qualquer registro alheio
        mvc.perform(get("/api/identity/usuarios/" + id + "/dados-pessoais")
                        .header("Authorization", bearer(tokenDe("administrador@empresa-b.dev"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonimizarSoDesativadoComConfirmacaoEPreservaAutoria() throws Exception {
        String admin = bearer(tokenDe("administrador@empresa-a.dev"));
        String email = emailAleatorio("anonimizar");
        UUID id = criarUsuario(EMPRESA_A, email, "Fulano de Tal", "VENDEDOR");
        Sessao sessao = entrar(email, SENHA, "Navegador do Fulano");
        String rota = "/api/identity/usuarios/" + id + "/anonimizar";

        mvc.perform(anonimizar(rota, admin, email))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("USUARIO_ATIVO"));

        mvc.perform(post("/api/identity/usuarios/" + id + "/desativar").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(anonimizar(rota, admin, "outro@teste.dev"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("CONFIRMACAO_INCORRETA"));
        mvc.perform(anonimizar(rota, bearer(tokenDe("gestor@empresa-a.dev")), email))
                .andExpect(status().isForbidden());

        mvc.perform(anonimizar(rota, admin, email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Usuário anonimizado"))
                .andExpect(jsonPath("$.data.email").value("anonimizado-" + id + "@invalido"))
                .andExpect(jsonPath("$.data.situacao").value("anonimizado"))
                .andExpect(jsonPath("$.data.perfis.length()").value(0));

        // Nada que identifique a pessoa ficou no cadastro, e ela não entra mais
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.usuarios
                 WHERE id = ? AND (senha_hash IS NOT NULL OR telefone IS NOT NULL OR mfa_secret IS NOT NULL)
                """, Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.tentativas_login WHERE email = ?::citext",
                Integer.class, email)).isZero();
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/identity/auth/refresh").cookie(sessao.cookie())).andExpect(status().isUnauthorized());

        // A auditoria fica como estava: o id segue sendo o autor dos registros
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.audit_logs WHERE usuario_id = ? AND acao = 'login'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT valor_novo::text FROM identity.audit_logs WHERE acao = 'anonimizar' AND entidade_id = ?
                """, String.class, id)).doesNotContain(email).doesNotContain("Fulano");

        // Os módulos que guardaram cópia do nome ficam sabendo
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Mensagem<DadosUsuarioAnonimizado>> evento = ArgumentCaptor.forClass(Mensagem.class);
        verify(rabbit).convertAndSend(eq("identity.eventos"), eq("identity.usuario.anonimizado"), evento.capture(),
                any(MessagePostProcessor.class));
        assertThat(evento.getValue().dados().usuarioId()).isEqualTo(id);
        assertThat(evento.getValue().tenantId().toString()).isEqualTo(EMPRESA_A);
        assertThat(evento.getValue().moduloOrigem()).isEqualTo("identity");

        // Não volta nem some da lista como outro usuário qualquer
        mvc.perform(post("/api/identity/usuarios/" + id + "/reativar").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("USUARIO_ANONIMIZADO"));
        mvc.perform(anonimizar(rota, admin, "anonimizado-" + id + "@invalido"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("JA_ANONIMIZADO"));
        mvc.perform(get("/api/identity/usuarios").param("situacao", "anonimizado").header("Authorization", admin))
                .andExpect(jsonPath("$.data.itens[?(@.id == '" + id + "')]").isNotEmpty());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder anonimizar(
            String rota, String autorizacao, String confirmacao) {
        return post(rota).header("Authorization", autorizacao).contentType(MediaType.APPLICATION_JSON)
                .content(json("confirmacao", texto(confirmacao)));
    }
}
