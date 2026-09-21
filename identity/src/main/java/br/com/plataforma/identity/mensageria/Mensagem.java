package br.com.plataforma.identity.mensageria;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de toda mensagem do RabbitMQ, igual em todos os módulos (Contrato §9.7).
 *
 * @param id           chave de idempotência: um id já processado é ignorado
 * @param tenantId     a única fonte de tenant numa mensagem, que não carrega token
 * @param correlacaoId X-Request-Id da requisição que originou o pedido
 */
public record Mensagem<T>(
        UUID id,
        String tipo,
        int versao,
        UUID tenantId,
        String moduloOrigem,
        OffsetDateTime ocorridoEm,
        UUID usuarioId,
        String correlacaoId,
        T dados) {

    /** Pedido de registro na timeline de uma empresa. */
    public record DadosTimeline(UUID empresaId, String tipoDoFato, String texto, String rota) {
    }

    /** Pedido de notificação para um usuário do mesmo tenant. */
    public record DadosNotificacao(UUID usuarioId, String categoria, String titulo, String texto, String rota) {
    }

    /** Pedido de e-mail de sistema (transacional). Campanha é do Marketing. */
    public record DadosEmail(String para, String modelo, Map<String, String> variaveis) {
    }
}
