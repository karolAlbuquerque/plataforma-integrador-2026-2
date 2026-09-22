package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Base dos testes de integração: PostgreSQL real em Testcontainers e perfil dev, que semeia os
 * usuários de teste. RabbitMQ e SMTP são substituídos por mocks.
 *
 * Todo login sai de um IP aleatório (X-Forwarded-For), para um teste não disparar o limite de
 * tentativas por IP de outro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.flyway.user=teste",
        "spring.flyway.password=teste",
        "spring.flyway.create-schemas=true",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "identity.config.diretorio=src/test/resources/config",
        "identity.saude.habilitada=false",
        "identity.url-publica=http://localhost:8080",
        "identity.admin-inicial.email=admin-inicial@centinela.dev",
        "identity.admin-inicial.nome=Administrador da Centinela",
        "SVC_LANDING_SEGREDO=segredo-do-landing-para-teste"
})
public abstract class BaseIntegracao {

    protected static final String LOGIN = "/api/identity/auth/login";
    protected static final String SENHA = "Plataforma2026";
    protected static final String EMPRESA_A = "a0000000-0000-4000-8000-00000000000a";
    protected static final String EMPRESA_B = "b0000000-0000-4000-8000-00000000000b";
    protected static final String EQUIPE_COMERCIAL_A = "a0000000-0000-4000-8000-0000000000e1";
    protected static final String EQUIPE_COMERCIAL_B = "b0000000-0000-4000-8000-0000000000e1";
    protected static final String TENANT_DE_PRODUCAO = "00000000-0000-4000-8000-000000000001";

    /** O token vai no fragmento do link — a única parte da URL que o navegador não envia ao servidor. */
    protected static final Pattern TOKEN_NO_LINK = Pattern.compile("/definir-senha#token=([A-Za-z0-9_-]+)");

    /** Sessão aberta: o access token do corpo e o cookie de refresh, como o navegador os guarda. */
    protected record Sessao(String token, Cookie cookie) {

        String autorizacao() {
            return bearer(token);
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("plataforma")
            .withUsername("teste")
            .withPassword("teste");

    static {
        POSTGRES.start();
    }

    @MockitoBean
    protected RabbitTemplate rabbit;

    @MockitoBean
    protected JavaMailSender correio;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Todo e-mail que "saiu" neste teste. Guardar o que chega é mais firme do que verificar o mock:
     * a recuperação de senha envia em segundo plano, depois de a requisição responder.
     */
    protected final List<SimpleMailMessage> enviados = new CopyOnWriteArrayList<>();

    @BeforeEach
    void registrarOsEmailsQueSairem() {
        doAnswer(chamada -> enviados.add(chamada.getArgument(0))).when(correio).send(any(SimpleMailMessage.class));
    }

    protected List<SimpleMailMessage> emailsPara(String destinatario) {
        return enviados.stream()
                .filter(mensagem -> mensagem.getTo() != null && List.of(mensagem.getTo()).contains(destinatario))
                .toList();
    }

    /** Espera o e-mail de convite ou de recuperação e devolve o token do link. */
    protected String tokenDoEmailPara(String destinatario, String inicioDoAssunto) {
        List<SimpleMailMessage> mensagens = await().atMost(Duration.ofSeconds(5))
                .until(() -> emailsPara(destinatario), recebidos -> !recebidos.isEmpty());
        SimpleMailMessage ultima = mensagens.get(mensagens.size() - 1);
        assertThat(ultima.getSubject()).startsWith(inicioDoAssunto);
        Matcher link = TOKEN_NO_LINK.matcher(ultima.getText());
        assertThat(link.find()).as("link com token no corpo do e-mail").isTrue();
        return link.group(1);
    }

    protected static String ipAleatorio() {
        ThreadLocalRandom sorteio = ThreadLocalRandom.current();
        return "10." + sorteio.nextInt(256) + "." + sorteio.nextInt(256) + "." + sorteio.nextInt(1, 255);
    }

    protected static String credenciais(String email, String senha) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
    }

    protected static MockHttpServletRequestBuilder login(String email, String senha, String ip) {
        return post(LOGIN)
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciais(email, senha));
    }

    /** Access token real, emitido pelo login com a senha dos usuários de teste. */
    protected String tokenDe(String email) throws Exception {
        String corpo = mvc.perform(login(email, SENHA, ipAleatorio()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.accessToken");
    }

    /** Login completo: o token e o cookie, para os testes que precisam renovar ou trocar a senha. */
    protected Sessao entrar(String email, String senha, String navegador) throws Exception {
        MockHttpServletResponse resposta = mvc.perform(login(email, senha, ipAleatorio()).header("User-Agent", navegador))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return new Sessao(JsonPath.read(resposta.getContentAsString(), "$.data.accessToken"),
                resposta.getCookie("refresh_token"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------- dados próprios de cada teste

    /**
     * O banco é o mesmo para toda a suíte: cada teste que escreve cria os seus usuários, com
     * e-mail aleatório, em vez de mexer nos semeados — que os outros testes esperam encontrar.
     */
    protected static String emailAleatorio(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.dev";
    }

    /** Usuário já com senha definida ({@link #SENHA}), pronto para entrar. */
    protected UUID criarUsuario(String tenant, String email, String nome, String... perfis) {
        return inserirUsuario(tenant, email, nome, hashDaSenhaDeTeste(), perfis);
    }

    /** Usuário recém-criado, ainda sem senha — como fica logo depois do convite. */
    protected UUID criarUsuarioSemSenha(String tenant, String email, String nome, String... perfis) {
        return inserirUsuario(tenant, email, nome, null, perfis);
    }

    private UUID inserirUsuario(String tenant, String email, String nome, String senhaHash, String... perfis) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.usuarios (id, tenant_id, nome, email, senha_hash)
                VALUES (?, ?::uuid, ?, ?::citext, ?)
                """, id, tenant, nome, email, senhaHash);
        for (String perfil : perfis) {
            darPerfil(tenant, id, perfil);
        }
        return id;
    }

    protected void darPerfil(String tenant, UUID usuario, String perfil) {
        jdbc.update("""
                INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                SELECT ?, id FROM identity.perfis WHERE tenant_id = ?::uuid AND nome = ? AND deleted_at IS NULL
                ON CONFLICT DO NOTHING
                """, usuario, tenant, perfil);
    }

    protected UUID perfilChamado(String tenant, String nome) {
        return jdbc.queryForObject("""
                SELECT id FROM identity.perfis WHERE tenant_id = ?::uuid AND nome = ? AND deleted_at IS NULL
                """, UUID.class, tenant, nome);
    }

    /**
     * Perfil montado pelo teste, com as permissões que ele precisa. Evita mexer nos dez perfis de
     * sistema, que valem para os demais testes.
     */
    protected UUID criarPerfil(String tenant, String nome, List<String> permissoes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.perfis (id, tenant_id, nome, sistema) VALUES (?, ?::uuid, ?, false)",
                id, tenant, nome);
        permissoes.forEach(codigo -> jdbc.update("""
                INSERT INTO identity.perfil_permissoes (perfil_id, permissao_id)
                SELECT ?, id FROM identity.permissoes WHERE codigo = ?
                """, id, codigo));
        return id;
    }

    /** Equipe própria do teste: as do seed têm membros contados por outros testes. */
    protected UUID criarEquipe(String tenant, String nome, UUID... membros) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.equipes (id, tenant_id, nome) VALUES (?, ?::uuid, ?)", id, tenant, nome);
        for (UUID membro : membros) {
            jdbc.update("INSERT INTO identity.usuario_equipes (usuario_id, equipe_id) VALUES (?, ?)", membro, id);
        }
        return id;
    }

    protected UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM identity.usuarios WHERE email = ?::citext AND deleted_at IS NULL",
                UUID.class, email);
    }

    /** Tenant só deste teste, sem perfis — o teste monta os que precisa com {@link #criarPerfil}. */
    protected String criarTenant(String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.tenants (id, razao_social, nome_fantasia, subdominio) VALUES (?, ?, ?, ?)
                """, id, nome + " Ltda.", nome, "t" + id.toString().substring(0, 8));
        return id.toString();
    }

    /** O hash de {@link #SENHA}, reaproveitado das sementes: o BCrypt 12 custa caro para gerar. */
    protected String hashDaSenhaDeTeste() {
        return jdbc.queryForObject("SELECT senha_hash FROM identity.usuarios WHERE email = 'administrador@empresa-a.dev'",
                String.class);
    }

    protected static String json(String... camposEValores) {
        StringBuilder texto = new StringBuilder("{");
        for (int i = 0; i < camposEValores.length; i += 2) {
            texto.append(i == 0 ? "" : ",").append('"').append(camposEValores[i]).append("\":").append(camposEValores[i + 1]);
        }
        return texto.append('}').toString();
    }

    protected static String texto(String valor) {
        return valor == null ? "null" : '"' + valor.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
