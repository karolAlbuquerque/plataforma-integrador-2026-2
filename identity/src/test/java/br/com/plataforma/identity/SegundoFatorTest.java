package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.plataforma.identity.segundofator.Base32;
import br.com.plataforma.identity.segundofator.Totp;
import jakarta.servlet.http.Cookie;

/**
 * Segundo fator obrigatório (Requisito RF10, decisão de 25/09), com a flag ligada como em
 * produção. Cada teste cria os seus usuários: os semeados continuam sem segundo fator para as
 * outras classes, que rodam com ele não obrigatório.
 */
@TestPropertySource(properties = "identity.segundo-fator.obrigatorio=true")
class SegundoFatorTest extends BaseIntegracao {

    private static final String SEGUNDO_FATOR = "/api/identity/auth/login/segundo-fator";
    private static final String CADASTRO = SEGUNDO_FATOR + "/cadastro";
    private static final String CONFIRMAR_CADASTRO = CADASTRO + "/confirmar";
    private static final String DA_CONTA = "/api/identity/conta/segundo-fator";

    /** O que o usuário tem depois do primeiro acesso: o segredo do aplicativo e a sessão. */
    private record Cadastrado(String segredo, String token, Cookie cookie, List<String> codigos) {
    }

    @Test
    void primeiroAcessoPedeCadastroEOPrimeiroCodigoAbreASessao() throws Exception {
        String email = emailAleatorio("2fa-cadastro");
        UUID id = criarUsuario(EMPRESA_A, email, "Maria do Cadastro", "VENDEDOR");
        String ip = ipAleatorio();

        // Senha certa: nada de token nem de cookie, só o desafio
        MockHttpServletResponse resposta = mvc.perform(login(email, SENHA, ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etapa").value("cadastro_segundo_fator"))
                .andExpect(jsonPath("$.data.desafioExpiraEm").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn().getResponse();
        assertThat(resposta.getCookie("refresh_token")).isNull();
        String desafio = JsonPath.read(resposta.getContentAsString(), "$.data.desafio");

        String cadastro = mvc.perform(corpo(CADASTRO, ip, json("desafio", texto(desafio))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn().getResponse().getContentAsString();
        String segredo = JsonPath.read(cadastro, "$.data.segredo");
        assertThat((String) JsonPath.read(cadastro, "$.data.uri")).contains("secret=" + segredo).contains("issuer=");

        // A tela recarregou: o mesmo segredo, senão o QR já lido deixaria de valer
        mvc.perform(corpo(CADASTRO, ip, json("desafio", texto(desafio))))
                .andExpect(jsonPath("$.data.segredo").value(segredo));

        mvc.perform(corpo(CONFIRMAR_CADASTRO, ip, json("desafio", texto(desafio), "codigo", texto(codigoErrado(segredo)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].codigo").value("CODIGO_INVALIDO"));

        MockHttpServletResponse sessao = mvc.perform(corpo(CONFIRMAR_CADASTRO, ip,
                        json("desafio", texto(desafio), "codigo", texto(codigoAgora(segredo)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.codigosRecuperacao.length()").value(10))
                .andReturn().getResponse();
        assertThat(sessao.getCookie("refresh_token")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT mfa_secret FROM identity.usuarios WHERE id = ?", String.class, id))
                .as("segredo cifrado, nunca em claro").isNotBlank().doesNotContain(segredo);

        // O desafio é de uso único
        mvc.perform(corpo(CONFIRMAR_CADASTRO, ip, json("desafio", texto(desafio), "codigo", texto(codigoAgora(segredo)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].codigo").value("DESAFIO_EXPIRADO"));

        assertThat(contarAuditoria(id, "ativar", "segundo_fator")).isEqualTo(1);
        assertThat(assuntos(email)).contains("Verificação em duas etapas ativada na sua conta");
    }

    @Test
    void comSegundoFatorAtivoOLoginPedeOCodigoQueNaoPodeSerReusado() throws Exception {
        String email = emailAleatorio("2fa-codigo");
        UUID id = criarUsuario(EMPRESA_A, email, "João do Código", "VENDEDOR");
        Cadastrado cadastrado = cadastrar(email);
        esquecerUltimoPasso(id);
        String ip = ipAleatorio();

        String desafio = desafio(email, ip, "segundo_fator");
        String codigo = codigoAgora(cadastrado.segredo());
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio), "codigo", texto(codigo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // O mesmo código, visto na tela de alguém, não abre outra sessão
        String outro = desafio(email, ip, "segundo_fator");
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(outro), "codigo", texto(codigo))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].codigo").value("CODIGO_INVALIDO"));

        // Código e código de recuperação juntos é pedido malformado
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(outro), "codigo", texto("123456"),
                        "codigoRecuperacao", texto(cadastrado.codigos().get(0)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codigoDeRecuperacaoValeUmaVezEAvisaPorEmail() throws Exception {
        String email = emailAleatorio("2fa-recuperacao");
        criarUsuario(EMPRESA_A, email, "Ana sem Celular", "VENDEDOR");
        Cadastrado cadastrado = cadastrar(email);
        String ip = ipAleatorio();
        // Minúsculas e sem hífen: o usuário digita do papel do jeito que der
        String codigo = cadastrado.codigos().get(3).replace("-", "").toLowerCase();

        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio(email, ip, "segundo_fator")),
                        "codigoRecuperacao", texto(codigo))))
                .andExpect(status().isOk());
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio(email, ip, "segundo_fator")),
                        "codigoRecuperacao", texto(codigo))))
                .andExpect(status().isUnauthorized());

        List<SimpleMailMessage> avisos = emailsPara(email).stream()
                .filter(m -> m.getSubject().startsWith("Um código de recuperação foi usado")).toList();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).getText()).contains("Restam 9 códigos");
    }

    @Test
    void cincoCodigosErradosMatamODesafioEBloqueiamOLogin() throws Exception {
        String email = emailAleatorio("2fa-forca-bruta");
        UUID id = criarUsuario(EMPRESA_A, email, "Alvo da Força Bruta", "VENDEDOR");
        Cadastrado cadastrado = cadastrar(email);
        esquecerUltimoPasso(id);
        String ip = ipAleatorio();

        String desafio = desafio(email, ip, "segundo_fator");
        for (int i = 0; i < 5; i++) {
            mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio), "codigo", texto(codigoErrado(cadastrado.segredo())))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errors[0].codigo").value("CODIGO_INVALIDO"));
        }
        // Nem o código certo passa mais neste desafio
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio), "codigo", texto(codigoAgora(cadastrado.segredo())))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].codigo").value("DESAFIO_EXPIRADO"));
        // E as cinco falhas contam no limite do login, por e-mail, de qualquer IP
        mvc.perform(login(email, SENHA, ipAleatorio())).andExpect(status().isTooManyRequests());
        assertThat(contarAuditoria(id, "segundo_fator_falhou", "sessao")).isEqualTo(5);
    }

    @Test
    void administradorRedefineQuemPerdeuOCelular() throws Exception {
        String admin = emailAleatorio("2fa-admin");
        criarUsuario(EMPRESA_A, admin, "Admin do 2FA", "ADMINISTRADOR");
        String tokenDoAdmin = cadastrar(admin).token();

        String email = emailAleatorio("2fa-perdeu");
        UUID id = criarUsuario(EMPRESA_A, email, "Pedro Perdeu", "VENDEDOR");
        Cadastrado cadastrado = cadastrar(email);

        mvc.perform(post("/api/identity/usuarios/" + id + "/segundo-fator/redefinir")
                        .header("Authorization", bearer(tokenDoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usuario.segundoFatorAtivo").value(false));

        // Sessões encerradas e, no próximo login, o cadastro do aplicativo de novo
        mvc.perform(post("/api/identity/auth/refresh").cookie(cadastrado.cookie())).andExpect(status().isUnauthorized());
        desafio(email, ipAleatorio(), "cadastro_segundo_fator");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.codigos_recuperacao WHERE usuario_id = ?",
                Integer.class, id)).isZero();
        assertThat(contarAuditoria(id, "redefinir", "segundo_fator")).isEqualTo(1);
        assertThat(assuntos(email)).contains("Sua verificação em duas etapas foi redefinida");

        // Quem não administra usuários não redefine
        mvc.perform(post("/api/identity/usuarios/" + id + "/segundo-fator/redefinir")
                        .header("Authorization", bearer(cadastrado.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void trocaDeAparelhoPedeSenhaECodigoEAposentaOSegredoAntigo() throws Exception {
        String email = emailAleatorio("2fa-troca");
        UUID id = criarUsuario(EMPRESA_A, email, "Carla Trocou", "VENDEDOR");
        Cadastrado cadastrado = cadastrar(email);
        String autorizacao = bearer(cadastrado.token());
        String ip = ipAleatorio();

        mvc.perform(get(DA_CONTA).header("Authorization", autorizacao))
                .andExpect(jsonPath("$.data.ativo").value(true))
                .andExpect(jsonPath("$.data.obrigatorio").value(true))
                .andExpect(jsonPath("$.data.codigosRestantes").value(10));

        mvc.perform(corpo(DA_CONTA + "/troca", ip, json("senha", texto("SenhaErrada1"))).header("Authorization", autorizacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("SENHA_INCORRETA"));
        mvc.perform(corpo(DA_CONTA + "/troca", ip, json("senha", texto(SENHA))).header("Authorization", autorizacao))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("CODIGO_OBRIGATORIO"));

        esquecerUltimoPasso(id);
        String troca = mvc.perform(corpo(DA_CONTA + "/troca", ip,
                        json("senha", texto(SENHA), "codigo", texto(codigoAgora(cadastrado.segredo()))))
                        .header("Authorization", autorizacao))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String novo = JsonPath.read(troca, "$.data.segredo");
        assertThat(novo).isNotEqualTo(cadastrado.segredo());

        mvc.perform(corpo(DA_CONTA + "/troca/confirmar", ip, json("codigo", texto(codigoErrado(novo))))
                        .header("Authorization", autorizacao))
                .andExpect(status().isBadRequest());
        mvc.perform(corpo(DA_CONTA + "/troca/confirmar", ip, json("codigo", texto(codigoAgora(novo))))
                        .header("Authorization", autorizacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codigosRecuperacao.length()").value(10));
        assertThat(contarAuditoria(id, "trocar_aparelho", "segundo_fator")).isEqualTo(1);

        // O aparelho antigo não entra mais; o novo, sim
        esquecerUltimoPasso(id);
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio(email, ip, "segundo_fator")),
                        "codigo", texto(codigoAgora(cadastrado.segredo())))))
                .andExpect(status().isUnauthorized());
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio(email, ip, "segundo_fator")),
                        "codigo", texto(codigoAgora(novo)))))
                .andExpect(status().isOk());
        // Os códigos de recuperação antigos também deixaram de valer
        mvc.perform(corpo(SEGUNDO_FATOR, ip, json("desafio", texto(desafio(email, ip, "segundo_fator")),
                        "codigoRecuperacao", texto(cadastrado.codigos().get(0)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeServicoNaoPassaPeloSegundoFator() throws Exception {
        mvc.perform(post("/api/identity/auth/token-servico").contentType(MediaType.APPLICATION_JSON)
                        .content(json("clientId", texto("landing"), "clientSecret", texto("segredo-do-landing-para-teste"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ------------------------------------------------------------------ apoio

    /** Primeiro acesso completo: senha, QR Code e primeiro código. */
    private Cadastrado cadastrar(String email) throws Exception {
        String ip = ipAleatorio();
        String desafio = desafio(email, ip, "cadastro_segundo_fator");
        String segredo = JsonPath.read(mvc.perform(corpo(CADASTRO, ip, json("desafio", texto(desafio))))
                .andReturn().getResponse().getContentAsString(), "$.data.segredo");
        MockHttpServletResponse resposta = mvc.perform(corpo(CONFIRMAR_CADASTRO, ip,
                        json("desafio", texto(desafio), "codigo", texto(codigoAgora(segredo)))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return new Cadastrado(segredo, JsonPath.read(resposta.getContentAsString(), "$.data.accessToken"),
                resposta.getCookie("refresh_token"), JsonPath.read(resposta.getContentAsString(), "$.data.codigosRecuperacao"));
    }

    private String desafio(String email, String ip, String etapaEsperada) throws Exception {
        ResultActions resposta = mvc.perform(login(email, SENHA, ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etapa").value(etapaEsperada));
        return JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.data.desafio");
    }

    /**
     * Os testes usam vários códigos seguidos, dentro dos mesmos 30 segundos; o anti-replay
     * recusaria o segundo. Aqui esquecemos o último passo usado — o anti-replay tem teste próprio.
     */
    private void esquecerUltimoPasso(UUID usuario) {
        jdbc.update("UPDATE identity.usuarios SET mfa_ultimo_passo = NULL WHERE id = ?", usuario);
    }

    private static String codigoAgora(String segredo) {
        return Totp.codigo(Base32.decodificar(segredo), Totp.passo(Instant.now()));
    }

    /** Um código de seis dígitos que não é o de nenhum dos três passos aceitos agora. */
    private static String codigoErrado(String segredo) {
        byte[] chave = Base32.decodificar(segredo);
        long passo = Totp.passo(Instant.now());
        List<String> validos = List.of(Totp.codigo(chave, passo - 1), Totp.codigo(chave, passo), Totp.codigo(chave, passo + 1));
        int candidato = 0;
        while (validos.contains(String.format("%06d", candidato))) {
            candidato++;
        }
        return String.format("%06d", candidato);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder corpo(String rota, String ip,
                                                                                                   String conteudo) {
        return post(rota).header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON).content(conteudo);
    }

    private int contarAuditoria(UUID usuario, String acao, String entidade) {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM identity.audit_logs WHERE entidade_id = ? AND acao = ? AND entidade = ?
                """, Integer.class, usuario, acao, entidade);
        return total == null ? 0 : total;
    }

    private List<String> assuntos(String email) {
        return emailsPara(email).stream().map(SimpleMailMessage::getSubject).toList();
    }
}
