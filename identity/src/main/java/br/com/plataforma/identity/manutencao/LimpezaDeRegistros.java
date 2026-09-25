package br.com.plataforma.identity.manutencao;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga, uma vez por dia, o que só serve por um tempo e cresceria sem limite: tentativas de
 * login (o bloqueio olha 15 minutos), ids de mensagens já processadas (a reentrega do RabbitMQ
 * acontece em segundos), sessões expiradas e links de recuperação vencidos.
 *
 * Não toca na auditoria, que é só de inserção (RF51), nem nos convites: a situação
 * "convite expirado" do usuário é calculada a partir deles.
 */
@Component
public class LimpezaDeRegistros {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDeRegistros.class);

    private final JdbcTemplate jdbc;
    private final Duration tentativasDeLogin;
    private final Duration mensagensProcessadas;
    private final Duration sessoesExpiradas;
    private final Duration recuperacoesVencidas;

    public LimpezaDeRegistros(JdbcTemplate jdbc,
                              @Value("${identity.limpeza.tentativas-de-login:30d}") Duration tentativasDeLogin,
                              @Value("${identity.limpeza.mensagens-processadas:90d}") Duration mensagensProcessadas,
                              @Value("${identity.limpeza.sessoes-expiradas:30d}") Duration sessoesExpiradas,
                              @Value("${identity.limpeza.recuperacoes-vencidas:30d}") Duration recuperacoesVencidas) {
        this.jdbc = jdbc;
        this.tentativasDeLogin = tentativasDeLogin;
        this.mensagensProcessadas = mensagensProcessadas;
        this.sessoesExpiradas = sessoesExpiradas;
        this.recuperacoesVencidas = recuperacoesVencidas;
    }

    @Scheduled(cron = "${identity.limpeza.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void agendada() {
        try {
            limpar();
        } catch (RuntimeException e) {
            log.error("Limpeza diária falhou; tenta de novo amanhã.", e);
        }
    }

    /** @return quantas linhas saíram de cada tabela */
    public Map<String, Integer> limpar() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Integer> apagadas = new LinkedHashMap<>();
        apagadas.put("tentativas_login", jdbc.update(
                "DELETE FROM identity.tentativas_login WHERE created_at < ?", agora.minus(tentativasDeLogin)));
        apagadas.put("eventos_processados", jdbc.update(
                "DELETE FROM identity.eventos_processados WHERE processado_em < ?", agora.minus(mensagensProcessadas)));
        apagadas.put("refresh_tokens", jdbc.update(
                "DELETE FROM identity.refresh_tokens WHERE expira_em < ?", agora.minus(sessoesExpiradas)));
        apagadas.put("recuperacoes_senha", jdbc.update(
                "DELETE FROM identity.recuperacoes_senha WHERE tipo = 'recuperacao' AND expira_em < ?",
                agora.minus(recuperacoesVencidas)));
        log.info("Limpeza diária: {}", apagadas);
        return apagadas;
    }
}
