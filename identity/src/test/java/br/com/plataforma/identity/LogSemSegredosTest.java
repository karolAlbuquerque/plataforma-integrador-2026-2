package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;

/**
 * Requisito RF09: "uma busca por senha nos logs de uma sessão completa de testes não retorna nenhum
 * valor em texto aberto". Aqui a sessão passa por login errado e certo, recuperação, definição,
 * troca de senha e um SMTP fora do ar — e nem as senhas nem os tokens dos links aparecem no log,
 * na auditoria ou nas respostas.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogSemSegredosTest extends BaseIntegracao {

    private static final String SENHA_ERRADA = "Errada#Unica8812";
    private static final String SENHA_DEFINIDA = "Definida#Unica4471";
    private static final String SENHA_TROCADA = "Trocada#Unica9053";

    @Test
    void nenhumaSenhaNemTokenNoLogNaAuditoriaOuNasRespostas(CapturedOutput log) throws Exception {
        String email = emailAleatorio("segredos");
        criarUsuario(EMPRESA_A, email, "Quem tem segredos", "TECNICO");
        StringBuilder respostas = new StringBuilder();

        respostas.append(mvc.perform(login(email, SENHA_ERRADA, ipAleatorio()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        respostas.append(mvc.perform(post("/api/identity/auth/senha/recuperar").header("X-Forwarded-For", ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON).content(json("email", texto(email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String tokenDeRecuperacao = tokenDoEmailPara(email, "Redefinição de senha");

        respostas.append(mvc.perform(post("/api/identity/auth/senha/definir").header("X-Forwarded-For", ipAleatorio())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", texto(tokenDeRecuperacao), "novaSenha", texto(SENHA_DEFINIDA))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        Sessao sessao = entrar(email, SENHA_DEFINIDA, "Chrome/140.0 (Windows NT 10.0)");
        respostas.append(mvc.perform(post("/api/identity/auth/senha").header("Authorization", sessao.autorizacao()).cookie(sessao.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("senhaAtual", texto(SENHA_DEFINIDA), "novaSenha", texto(SENHA_TROCADA))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // SMTP fora do ar no convite: o aviso vai para o log, sem o corpo e sem o link
        doAnswer(chamada -> {
            enviados.add(chamada.getArgument(0));   // guarda o que teria saído, para conhecer o token
            throw new MailSendException("conexão recusada");
        }).when(correio).send(any(SimpleMailMessage.class));
        String convidado = emailAleatorio("convite-sem-smtp");
        UUID id = criarUsuarioSemSenha(EMPRESA_A, convidado, "Convidado sem SMTP", "TECNICO");
        respostas.append(mvc.perform(post("/api/identity/usuarios/" + id + "/convite").header("X-Forwarded-For", ipAleatorio())
                        .header("Authorization", bearer(tokenDe("administrador@empresa-a.dev"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String tokenDoConvite = tokenDoEmailPara(convidado, "Seu acesso");

        List<String> segredos = List.of(SENHA_ERRADA, SENHA_DEFINIDA, SENHA_TROCADA, SENHA, tokenDeRecuperacao, tokenDoConvite,
                sessao.cookie().getValue());
        String auditoria = String.join("\n", jdbc.queryForList(
                "SELECT coalesce(valor_anterior::text, '') || coalesce(valor_novo::text, '') FROM identity.audit_logs", String.class));

        assertThat(log.getAll()).as("log").doesNotContain(segredos).contains("não enviado").doesNotContain(convidado);
        assertThat(auditoria).as("auditoria").doesNotContain(segredos);
        assertThat(respostas.toString()).as("respostas").doesNotContain(segredos);
    }
}
