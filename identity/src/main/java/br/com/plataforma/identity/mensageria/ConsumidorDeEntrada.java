package br.com.plataforma.identity.mensageria;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.mensageria.Mensagem.DadosEmail;
import br.com.plataforma.identity.mensageria.Mensagem.DadosNotificacao;
import br.com.plataforma.identity.mensageria.Mensagem.DadosTimeline;
import br.com.plataforma.identity.observabilidade.CorrelacaoFiltro;

/**
 * Recebe os pedidos de identity.entrada. Exceção aqui faz o RabbitMQ reentregar; na terceira
 * falha a mensagem vai para a .dlq da fila (application.yml, spring.rabbitmq.listener).
 *
 * Quem pede é conferido pela propriedade AMQP user_id: o RabbitMQ só aceita nela o usuário da
 * conexão, mq_{modulo}, e aqui ela precisa corresponder ao moduloOrigem do envelope. Sem isso, o
 * CRM poderia pedir e-mail com os modelos do Financeiro (identity.asyncapi.yaml 0.2.0).
 */
@Component
public class ConsumidorDeEntrada {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeEntrada.class);

    private final ProcessadorDePedidos processador;
    private final boolean exigirUserId;

    public ConsumidorDeEntrada(ProcessadorDePedidos processador,
                               @Value("${identity.mensageria.exigir-user-id:true}") boolean exigirUserId) {
        this.processador = processador;
        this.exigirUserId = exigirUserId;
        if (!exigirUserId) {
            log.warn("identity.mensageria.exigir-user-id=false: pedidos de identity.entrada aceitos sem conferir o remetente.");
        }
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_TIMELINE)
    public void registrarNaTimeline(Mensagem<DadosTimeline> mensagem,
                                    @Header(name = AmqpHeaders.RECEIVED_USER_ID, required = false) String remetente) {
        tratar(mensagem, remetente, () -> processador.registrarNaTimeline(mensagem));
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_NOTIFICACAO)
    public void criarNotificacao(Mensagem<DadosNotificacao> mensagem,
                                 @Header(name = AmqpHeaders.RECEIVED_USER_ID, required = false) String remetente) {
        tratar(mensagem, remetente, () -> processador.criarNotificacao(mensagem));
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_EMAIL)
    public void enviarEmail(Mensagem<DadosEmail> mensagem,
                            @Header(name = AmqpHeaders.RECEIVED_USER_ID, required = false) String remetente) {
        tratar(mensagem, remetente, () -> processador.enviarEmail(mensagem));
    }

    private void tratar(Mensagem<?> mensagem, String remetente, BooleanSupplier efeito) {
        if (mensagem.correlacaoId() != null && mensagem.correlacaoId().length() <= 100) {
            MDC.put(CorrelacaoFiltro.CHAVE_MDC, mensagem.correlacaoId());
        }
        try {
            conferirRemetente(mensagem, remetente);
            if (!efeito.getAsBoolean()) {
                log.info("Mensagem {} ({}) já processada; ignorada.", mensagem.tipo(), mensagem.id());
            }
        } catch (MensagemRecusadaException e) {
            log.warn("Mensagem {} ({}) de {} recusada: {}", mensagem.tipo(), mensagem.id(), mensagem.moduloOrigem(),
                    e.getMessage());
            throw e;
        } finally {
            MDC.remove(CorrelacaoFiltro.CHAVE_MDC);
        }
    }

    private void conferirRemetente(Mensagem<?> mensagem, String remetente) {
        if (!exigirUserId) {
            return;
        }
        if (remetente == null || remetente.isBlank()) {
            throw new MensagemRecusadaException("sem a propriedade user_id; publique com user_id = mq_{modulo}.");
        }
        if (!remetente.equals("mq_" + mensagem.moduloOrigem())) {
            throw new MensagemRecusadaException("moduloOrigem " + mensagem.moduloOrigem()
                    + " não corresponde ao remetente " + remetente + ".");
        }
    }
}
