package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Convite, recuperação e troca de senha — Requisitos RF07, RF08, RF09 e RF14. */
class SenhaTest extends BaseIntegracao {

    private static final String RECUPERAR = "/api/identity/auth/senha/recuperar";
    private static final String VERIFICAR = "/api/identity/auth/senha/verificar";
    private static final String DEFINIR = "/api/identity/auth/senha/definir";
    private static final String TROCAR = "/api/identity/auth/senha";
    private static final String REFRESH = "/api/identity/auth/refresh";
    private static final String NOVA_SENHA = "Centinela2026";
    private static final String MESMA_RESPOSTA = "Se o e-mail estiver cadastrado, enviaremos um link em instantes.";

    // ------------------------------------------------------------------ convite

    @Test
    void conviteDefineASenhaEOLinkSoServeUmaVez() throws Exception {
        String email = emailAleatorio("convidado");
        UUID id = criarUsuarioSemSenha(EMPRESA_A, email, "Convidado da Empresa A", "VENDEDOR");
        String token = convidar(id, email);

        mvc.perform(corpo(VERIFICAR, json("token", texto(token))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.tipo").value("convite"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nome").value("Convidado da Empresa A"));

        mvc.perform(corpo(DEFINIR, json("token", texto(token), "novaSenha", texto("curta1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("SENHA_FRACA"))
                .andExpect(jsonPath("$.errors[0].campo").value("novaSenha"));

        mvc.perform(corpo(DEFINIR, json("token", texto(token), "novaSenha", texto(NOVA_SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Uso único: o mesmo link não vale de novo, nem para verificar
        mvc.perform(corpo(DEFINIR, json("token", texto(token), "novaSenha", texto(NOVA_SENHA))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("LINK_INVALIDO"));
        mvc.perform(corpo(VERIFICAR, json("token", texto(token)))).andExpect(status().isUnprocessableEntity());

        mvc.perform(login(email, NOVA_SENHA, ipAleatorio())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs
                 WHERE acao = 'definir_senha' AND entidade_id = ? AND valor_novo->>'via' = 'convite'
                """, Integer.class, id)).isEqualTo(1);
    }

    @Test
    void oLinkDoConviteVaiNoFragmentoEComAEmpresaNoAssunto() throws Exception {
        String email = emailAleatorio("link");
        UUID id = criarUsuarioSemSenha(EMPRESA_A, email, "Quem recebe o link", "TECNICO");
        String token = convidar(id, email);

        SimpleMailMessage mensagem = emailsPara(email).get(0);
        assertThat(mensagem.getSubject()).isEqualTo("Seu acesso à plataforma da Empresa A");
        assertThat(mensagem.getText())
                .contains("http://localhost:8080/definir-senha#token=" + token)
                .contains("Quem recebe o link", email)
                .contains("Mensagem automática da plataforma");
    }

    @Test
    void conviteNovoCancelaOAnterior() throws Exception {
        String email = emailAleatorio("reconvidado");
        UUID id = criarUsuarioSemSenha(EMPRESA_A, email, "Reconvidado", "TECNICO");
        String primeiro = convidar(id, email);
        String segundo = convidar(id, email);

        assertThat(segundo).isNotEqualTo(primeiro);
        mvc.perform(corpo(VERIFICAR, json("token", texto(primeiro)))).andExpect(status().isUnprocessableEntity());
        mvc.perform(corpo(VERIFICAR, json("token", texto(segundo)))).andExpect(status().isOk());
    }

    @Test
    void linkDeUsuarioDesativadoNaoVale() throws Exception {
        String email = emailAleatorio("desativado");
        UUID id = criarUsuarioSemSenha(EMPRESA_A, email, "Desativado", "TECNICO");
        String token = convidar(id, email);
        jdbc.update("UPDATE identity.usuarios SET ativo = false WHERE id = ?", id);

        mvc.perform(corpo(VERIFICAR, json("token", texto(token)))).andExpect(status().isUnprocessableEntity());
        mvc.perform(corpo(DEFINIR, json("token", texto(token), "novaSenha", texto(NOVA_SENHA))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("LINK_INVALIDO"));
    }

    @Test
    void tokenInventadoOuVazioNaoVaza() throws Exception {
        mvc.perform(corpo(VERIFICAR, json("token", texto("token-que-nao-existe"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Este link é inválido ou já expirou. Peça um novo."));
        mvc.perform(corpo(VERIFICAR, json("token", texto(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("token"));
    }

    // ------------------------------------------------------------------ recuperação

    @Test
    void recuperacaoTrocaASenhaEEncerraAsSessoes() throws Exception {
        String email = emailAleatorio("esqueci");
        criarUsuario(EMPRESA_A, email, "Quem esqueceu a senha", "FINANCEIRO");
        Sessao sessao = entrar(email, SENHA, "Mozilla/5.0 (Windows NT 10.0) Chrome/140.0 Safari/537.36");

        mvc.perform(corpo(RECUPERAR, json("email", texto(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MESMA_RESPOSTA));

        String token = tokenDoEmailPara(email, "Redefinição de senha");
        mvc.perform(corpo(VERIFICAR, json("token", texto(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tipo").value("recuperacao"));
        mvc.perform(corpo(DEFINIR, json("token", texto(token), "novaSenha", texto(NOVA_SENHA))))
                .andExpect(status().isOk());

        // A sessão aberta antes da troca não renova mais (RF07)
        mvc.perform(post(REFRESH).cookie(sessao.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isUnauthorized());
        mvc.perform(login(email, NOVA_SENHA, ipAleatorio())).andExpect(status().isOk());
    }

    @Test
    void recuperacaoDeEmailInexistenteRespondeIgualENaoEnviaNada() throws Exception {
        String inexistente = emailAleatorio("ninguem");
        mvc.perform(corpo(RECUPERAR, json("email", texto(inexistente))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(MESMA_RESPOSTA));

        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs
                 WHERE acao = 'recuperacao_ignorada' AND valor_novo->>'email' = ?
                   AND valor_novo->>'motivo' = 'email_inexistente_ou_inativo'
                """, Integer.class, inexistente), total -> total == 1);
        assertThat(emailsPara(inexistente)).isEmpty();
    }

    @Test
    void doisPedidosSeguidosParaOMesmoEmailMandamUmEmailSo() throws Exception {
        String email = emailAleatorio("insistente");
        UUID id = criarUsuario(EMPRESA_A, email, "Quem insiste", "TECNICO");

        mvc.perform(corpo(RECUPERAR, json("email", texto(email)))).andExpect(status().isOk());
        tokenDoEmailPara(email, "Redefinição de senha");
        mvc.perform(corpo(RECUPERAR, json("email", texto(email)))).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs
                 WHERE acao = 'recuperacao_ignorada' AND entidade_id = ? AND valor_novo->>'motivo' = 'limite_por_email'
                """, Integer.class, id), total -> total == 1);
        assertThat(emailsPara(email)).hasSize(1);
    }

    @Test
    void emailInvalidoResponde400() throws Exception {
        mvc.perform(corpo(RECUPERAR, json("email", texto("nao-e-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("email"));
    }

    // ------------------------------------------------------------------ troca pelo próprio usuário

    @Test
    void trocaMantemASessaoAtualEDerrubaAsOutras() throws Exception {
        String email = emailAleatorio("troca");
        criarUsuario(EMPRESA_A, email, "Quem troca a senha", "MARKETING");
        Sessao aqui = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");
        Sessao outroAparelho = entrar(email, SENHA, "Firefox/130.0 (X11; Linux x86_64)");

        mvc.perform(corpo(TROCAR, json("senhaAtual", texto(SENHA), "novaSenha", texto(NOVA_SENHA)))
                        .header("Authorization", aqui.autorizacao())
                        .cookie(aqui.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha trocada. As outras sessões foram encerradas."));

        mvc.perform(post(REFRESH).cookie(aqui.cookie())).andExpect(status().isOk());
        mvc.perform(post(REFRESH).cookie(outroAparelho.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(login(email, NOVA_SENHA, ipAleatorio())).andExpect(status().isOk());
    }

    @Test
    void trocaComSenhaAtualErradaResponde422EContaComoTentativa() throws Exception {
        String email = emailAleatorio("senha-errada");
        criarUsuario(EMPRESA_A, email, "Quem erra a senha atual", "TECNICO");
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");

        mvc.perform(corpo(TROCAR, json("senhaAtual", texto("nao-e-a-minha"), "novaSenha", texto(NOVA_SENHA)))
                        .header("Authorization", sessao.autorizacao())
                        .cookie(sessao.cookie()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("SENHA_ATUAL_INCORRETA"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.tentativas_login WHERE email = ?::citext AND NOT sucesso
                """, Integer.class, email)).isEqualTo(1);
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isOk());
    }

    @Test
    void trocaComSenhaFracaResponde400ESemTokenResponde401() throws Exception {
        String email = emailAleatorio("fraca");
        criarUsuario(EMPRESA_A, email, "Quem tenta senha fraca", "TECNICO");
        Sessao sessao = entrar(email, SENHA, "Chrome/140.0 (Windows NT 10.0)");

        mvc.perform(corpo(TROCAR, json("senhaAtual", texto(SENHA), "novaSenha", texto("12345678")))
                        .header("Authorization", sessao.autorizacao())
                        .cookie(sessao.cookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("SENHA_FRACA"))
                .andExpect(jsonPath("$.message").value("A senha precisa ter letras e números."));

        mvc.perform(corpo(TROCAR, json("senhaAtual", texto(SENHA), "novaSenha", texto(NOVA_SENHA))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void smtpForaDoArNaoDerrubaOCadastroEOConviteContinuaValendo() throws Exception {
        doThrow(new MailSendException("SMTP fora do ar")).when(correio).send(any(SimpleMailMessage.class));
        String email = emailAleatorio("sem-smtp");

        mvc.perform(corpo("/api/identity/usuarios",
                        json("nome", texto("Quem não recebeu o convite"), "email", texto(email),
                                "perfis", "[" + texto(perfilChamado(EMPRESA_A, "TECNICO").toString()) + "]"))
                        .header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conviteEnviado").value(false))
                .andExpect(jsonPath("$.data.usuario.situacao").value("convite_pendente"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.recuperacoes_senha r
                  JOIN identity.usuarios u ON u.id = r.usuario_id
                 WHERE u.email = ?::citext AND r.tipo = 'convite' AND r.usado_em IS NULL
                """, Integer.class, email)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ apoio

    private static MockHttpServletRequestBuilder corpo(String rota, String corpo) {
        return post(rota).header("X-Forwarded-For", ipAleatorio())
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    /** Emite o convite pela rota de administração e devolve o token que foi parar no e-mail. */
    private String convidar(UUID usuario, String email) throws Exception {
        mvc.perform(post("/api/identity/usuarios/" + usuario + "/convite")
                        .header("X-Forwarded-For", ipAleatorio())
                        .header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conviteEnviado").value(true));
        return tokenDoEmailPara(email, "Seu acesso à plataforma");
    }
}
