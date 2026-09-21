package br.com.plataforma.identity.mensageria;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pedidos que os módulos fazem à plataforma (Contrato §9.7, contratos/identity.asyncapi.yaml).
 * Cada tipo de pedido tem uma fila, ligada à exchange identity.entrada pela routing key igual ao
 * tipo, e uma .dlq para onde vai a mensagem que falhou três vezes.
 */
@Configuration
public class TopologiaMensageria {

    public static final String EXCHANGE_ENTRADA = "identity.entrada";
    static final String DLX = "identity.dlx";

    public static final String TIPO_TIMELINE = "identity.timeline.registrar";
    public static final String TIPO_NOTIFICACAO = "identity.notificacao.criar";
    public static final String TIPO_EMAIL = "identity.email.enviar";

    static final String FILA_TIMELINE = "identity.timeline-registrar";
    static final String FILA_NOTIFICACAO = "identity.notificacao-criar";
    static final String FILA_EMAIL = "identity.email-enviar";

    private static final Map<String, String> FILA_POR_TIPO = Map.of(
            FILA_TIMELINE, TIPO_TIMELINE,
            FILA_NOTIFICACAO, TIPO_NOTIFICACAO,
            FILA_EMAIL, TIPO_EMAIL);

    /** Criada também pelo rabbitmq/init.sh do infra; declarar de novo, igual, não muda nada. */
    @Bean
    TopicExchange exchangeDeEntrada() {
        return new TopicExchange(EXCHANGE_ENTRADA, true, false);
    }

    @Bean
    DirectExchange exchangeDeMensagensComFalha() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Declarables filasDeEntrada(TopicExchange exchangeDeEntrada, DirectExchange exchangeDeMensagensComFalha) {
        List<Declarable> declaracoes = new ArrayList<>();
        FILA_POR_TIPO.forEach((nome, tipo) -> {
            Queue fila = QueueBuilder.durable(nome)
                    .deadLetterExchange(DLX)
                    .deadLetterRoutingKey(nome + ".dlq")
                    .build();
            Queue dlq = QueueBuilder.durable(nome + ".dlq").build();
            declaracoes.add(fila);
            declaracoes.add(dlq);
            declaracoes.add(BindingBuilder.bind(fila).to(exchangeDeEntrada).with(tipo));
            declaracoes.add(BindingBuilder.bind(dlq).to(exchangeDeMensagensComFalha).with(nome + ".dlq"));
        });
        return new Declarables(declaracoes);
    }

    /** JSON nos dois sentidos. O tipo vem do parâmetro do listener, não de cabeçalho Java de quem publicou. */
    @Bean
    MessageConverter conversorJson(ObjectMapper mapper) {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(mapper);
        conversor.setAlwaysConvertToInferredType(true);
        return conversor;
    }
}
