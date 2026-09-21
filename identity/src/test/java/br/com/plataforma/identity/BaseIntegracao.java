package br.com.plataforma.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

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
        "SVC_LANDING_SEGREDO=segredo-do-landing-para-teste"
})
public abstract class BaseIntegracao {

    protected static final String LOGIN = "/api/identity/auth/login";
    protected static final String SENHA = "Plataforma2026";
    protected static final String EMPRESA_A = "a0000000-0000-4000-8000-00000000000a";
    protected static final String EMPRESA_B = "b0000000-0000-4000-8000-00000000000b";
    protected static final String EQUIPE_COMERCIAL_A = "a0000000-0000-4000-8000-0000000000e1";
    protected static final String EQUIPE_COMERCIAL_B = "b0000000-0000-4000-8000-0000000000e1";

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

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
