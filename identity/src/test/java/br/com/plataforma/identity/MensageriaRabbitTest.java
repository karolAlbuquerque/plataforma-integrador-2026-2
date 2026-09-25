package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import br.com.plataforma.identity.mensageria.Mensagem;
import br.com.plataforma.identity.mensageria.Mensagem.DadosTimeline;
import br.com.plataforma.identity.mensageria.TopologiaMensageria;

/**
 * identity.entrada com um RabbitMQ de verdade (Requisito RNF08, decisão D10): a topologia que o
 * identity declara, o listener ligado, a idempotência com reentrega e a .dlq depois das
 * tentativas. Os outros testes chamam o consumidor direto, sem broker.
 *
 * Os pedidos saem como sairiam do CRM: conectado como mq_crm e com user_id = mq_crm. O template
 * do Spring (guest) só publica o que precisa ser recusado.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.flyway.user=teste",
        "spring.flyway.password=teste",
        "spring.flyway.create-schemas=true",
        "spring.rabbitmq.virtual-host=/",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=2",
        "identity.config.diretorio=src/test/resources/config",
        "identity.saude.habilitada=false"
})
class MensageriaRabbitTest {

    private static final String FILA_DLQ = "identity.timeline-registrar.dlq";
    private static final String USUARIO_DO_CRM = "mq_crm";

    /** O mesmo PostgreSQL dos outros testes; o RabbitMQ é só desta classe. */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = BaseIntegracao.POSTGRES;

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static {
        RABBIT.start();
        try {
            RABBIT.execInContainer("rabbitmqctl", "add_user", USUARIO_DO_CRM, "senha-do-crm");
            RABBIT.execInContainer("rabbitmqctl", "set_permissions", "-p", "/", USUARIO_DO_CRM, ".*", ".*", ".*");
        } catch (Exception e) {
            throw new IllegalStateException("Usuário mq_crm não criado no RabbitMQ de teste.", e);
        }
    }

    @MockitoBean
    JavaMailSender correio;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MessageConverter conversor;

    /** Publica como o CRM: conexão de mq_crm; com ou sem a propriedade user_id. */
    CachingConnectionFactory conexao;
    RabbitTemplate crm;
    RabbitTemplate crmSemUserId;

    @BeforeEach
    void conectarComoCrm() {
        conexao = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexao.setUsername(USUARIO_DO_CRM);
        conexao.setPassword("senha-do-crm");
        crm = new RabbitTemplate(conexao);
        crm.setMessageConverter(conversor);
        crm.setBeforePublishPostProcessors(mensagem -> {
            mensagem.getMessageProperties().setUserId(USUARIO_DO_CRM);
            return mensagem;
        });
        crmSemUserId = new RabbitTemplate(conexao);
        crmSemUserId.setMessageConverter(conversor);
    }

    @AfterEach
    void desconectar() {
        conexao.destroy();
    }

    @Test
    void pedidoPublicadoNaExchangeEGravadoUmaVezMesmoReentregue() {
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = timeline(BaseIntegracao.EMPRESA_A, empresa, "Proposta P-7 enviada");

        crm.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE, mensagem);
        crm.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE, mensagem);

        await().atMost(Duration.ofSeconds(10)).until(() -> contar(empresa), total -> total >= 1);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> contar(empresa), total -> total == 1);
        assertThat(jdbc.queryForObject("SELECT texto FROM identity.eventos_timeline WHERE empresa_id = ?", String.class, empresa))
                .isEqualTo("Proposta P-7 enviada");
    }

    @Test
    void pedidoQueNuncaVaiDarCertoTerminaNaDlq() {
        // Tenant inexistente: recusado nas três tentativas e encaminhado para a .dlq
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = timeline(UUID.randomUUID().toString(), empresa, "De tenant que não existe");

        crm.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE, mensagem);

        Message morta = rabbit.receive(FILA_DLQ, 15_000);
        assertThat(morta).as("mensagem na .dlq").isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).contains(mensagem.id().toString());
        assertThat(contar(empresa)).isZero();
    }

    @Test
    void corpoQueNaoEJsonTerminaNaDlqSemDerrubarOListener() {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE,
                new Message("{isto não é json".getBytes(StandardCharsets.UTF_8), propriedades));

        Message morta = rabbit.receive(FILA_DLQ, 15_000);
        assertThat(morta).as("mensagem na .dlq").isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).isEqualTo("{isto não é json");

        // O listener continua de pé: um pedido válido depois do inválido é processado
        UUID empresa = UUID.randomUUID();
        crm.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE,
                timeline(BaseIntegracao.EMPRESA_A, empresa, "Depois do inválido"));
        await().atMost(Duration.ofSeconds(10)).until(() -> contar(empresa), total -> total == 1);
    }

    @Test
    void pedidoSemUserIdTerminaNaDlq() {
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = timeline(BaseIntegracao.EMPRESA_A, empresa, "Sem user_id");

        crmSemUserId.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE, mensagem);

        Message morta = rabbit.receive(FILA_DLQ, 15_000);
        assertThat(morta).as("mensagem na .dlq").isNotNull();
        assertThat(new String(morta.getBody(), StandardCharsets.UTF_8)).contains(mensagem.id().toString());
        assertThat(contar(empresa)).isZero();
    }

    @Test
    void brokerRecusaUserIdDeOutroUsuario() {
        // O guest se dizendo mq_crm: o RabbitMQ fecha o canal e a mensagem nem chega à fila
        UUID empresa = UUID.randomUUID();
        Mensagem<DadosTimeline> mensagem = timeline(BaseIntegracao.EMPRESA_A, empresa, "Fingindo ser o CRM");

        rabbit.convertAndSend(TopologiaMensageria.EXCHANGE_ENTRADA, TopologiaMensageria.TIPO_TIMELINE, mensagem,
                publicada -> {
                    publicada.getMessageProperties().setUserId(USUARIO_DO_CRM);
                    return publicada;
                });

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() -> contar(empresa), total -> total == 0);
        Message morta = rabbit.receive(FILA_DLQ, 500);
        assertThat(morta == null ? "" : new String(morta.getBody(), StandardCharsets.UTF_8))
                .doesNotContain(mensagem.id().toString());
    }

    private int contar(UUID empresa) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM identity.eventos_timeline WHERE empresa_id = ?", Integer.class, empresa);
        return total == null ? 0 : total;
    }

    private static Mensagem<DadosTimeline> timeline(String tenant, UUID empresa, String texto) {
        return new Mensagem<>(UUID.randomUUID(), TopologiaMensageria.TIPO_TIMELINE, 1, UUID.fromString(tenant), "crm",
                OffsetDateTime.now(ZoneOffset.UTC), null, "teste-" + UUID.randomUUID(),
                new DadosTimeline(empresa, "crm.proposta.enviada", texto, null));
    }
}
