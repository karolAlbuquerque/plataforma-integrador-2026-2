package br.com.plataforma.identity.mensageria;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.mensageria.Mensagem.DadosEmail;
import br.com.plataforma.identity.mensageria.Mensagem.DadosNotificacao;
import br.com.plataforma.identity.mensageria.Mensagem.DadosTimeline;
import br.com.plataforma.identity.observabilidade.CorrelacaoFiltro;

/**
 * Recebe os pedidos de identity.entrada. Exceção aqui faz o RabbitMQ reentregar; na terceira
 * falha a mensagem vai para a .dlq da fila (application.yml, spring.rabbitmq.listener).
 */
@Component
public class ConsumidorDeEntrada {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeEntrada.class);

    private final ProcessadorDePedidos processador;

    public ConsumidorDeEntrada(ProcessadorDePedidos processador) {
        this.processador = processador;
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_TIMELINE)
    public void registrarNaTimeline(Mensagem<DadosTimeline> mensagem) {
        tratar(mensagem, () -> processador.registrarNaTimeline(mensagem));
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_NOTIFICACAO)
    public void criarNotificacao(Mensagem<DadosNotificacao> mensagem) {
        tratar(mensagem, () -> processador.criarNotificacao(mensagem));
    }

    @RabbitListener(queues = TopologiaMensageria.FILA_EMAIL)
    public void enviarEmail(Mensagem<DadosEmail> mensagem) {
        tratar(mensagem, () -> processador.enviarEmail(mensagem));
    }

    private void tratar(Mensagem<?> mensagem, BooleanSupplier efeito) {
        if (mensagem.correlacaoId() != null && mensagem.correlacaoId().length() <= 100) {
            MDC.put(CorrelacaoFiltro.CHAVE_MDC, mensagem.correlacaoId());
        }
        try {
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
}
