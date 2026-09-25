package br.com.plataforma.identity.mensageria;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos do identity na exchange identity.eventos (Contrato §9.7), com o envelope de
 * sempre e a propriedade AMQP user_id = o usuário do RabbitMQ, que o broker confere.
 *
 * Chamar depois do commit. Sem outbox por enquanto (decisão D10): se o RabbitMQ estiver fora do ar,
 * o evento se perde e fica o aviso no log — por isso só publicamos fatos que os módulos também
 * conseguem descobrir de outro jeito.
 */
@Component
public class PublicadorDeEventos {

    public static final String EXCHANGE_EVENTOS = "identity.eventos";
    public static final String USUARIO_ANONIMIZADO = "identity.usuario.anonimizado";

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);

    /** Dados de identity.usuario.anonimizado: quem teve os dados pessoais apagados. */
    public record DadosUsuarioAnonimizado(UUID usuarioId) {
    }

    private final RabbitTemplate rabbit;
    private final String usuarioDoBroker;

    public PublicadorDeEventos(RabbitTemplate rabbit, @Value("${spring.rabbitmq.username}") String usuarioDoBroker) {
        this.rabbit = rabbit;
        this.usuarioDoBroker = usuarioDoBroker;
    }

    /** @return false se o broker recusou ou está fora do ar */
    public <T> boolean publicar(String tipo, UUID tenant, UUID usuario, T dados) {
        Mensagem<T> mensagem = new Mensagem<>(UUID.randomUUID(), tipo, 1, tenant, "identity",
                OffsetDateTime.now(ZoneOffset.UTC), usuario, MDC.get("requestId"), dados);
        try {
            rabbit.convertAndSend(EXCHANGE_EVENTOS, tipo, mensagem, propriedades -> {
                propriedades.getMessageProperties().setUserId(usuarioDoBroker);
                propriedades.getMessageProperties().setMessageId(mensagem.id().toString());
                return propriedades;
            });
            return true;
        } catch (AmqpException e) {
            log.warn("Evento {} {} não publicado: {}", tipo, mensagem.id(), e.getMessage());
            return false;
        }
    }
}
