package br.com.plataforma.gateway;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;

/**
 * Limite de requisições por IP nas rotas públicas (Requisito RF45, Contrato §12.6): a única porta
 * do sistema aberta a quem não fez login. Janela fixa de um minuto, em memória — há um gateway só.
 * Excedido o limite, 429 no envelope com Retry-After, sem afetar as rotas autenticadas.
 *
 * O IP é o da conexão TCP (forward-headers-strategy: none). Com um proxy na frente do gateway,
 * todos os visitantes chegariam com o IP dele: antes disso, configurar os proxies confiáveis.
 */
@Component
public class LimitePublicoFiltro implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LimitePublicoFiltro.class);
    private static final long MINUTO = 60_000;

    /** O mesmo casamento de caminho da rota /public/{m}/** (Rotas.java): o que ela encaminha, este filtro conta. */
    private static final PathPattern PUBLICAS = PathPatternParser.defaultInstance.parse("/public/**");

    private record Janela(long minuto, AtomicInteger contagem) {
    }

    private final int limitePorMinuto;
    private final EscritorDeErro erros;
    private final Clock relogio;
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private volatile long ultimaLimpeza;

    @Autowired
    public LimitePublicoFiltro(@Value("${gateway.limite-publico-por-minuto:120}") int limitePorMinuto, EscritorDeErro erros) {
        this(limitePorMinuto, erros, Clock.systemUTC());
    }

    LimitePublicoFiltro(int limitePorMinuto, EscritorDeErro erros, Clock relogio) {
        this.limitePorMinuto = limitePorMinuto;
        this.erros = erros;
        this.relogio = relogio;
        if (limitePorMinuto <= 0) {
            log.warn("LIMITE_PUBLICO_POR_MINUTO desligado: as rotas /public/** ficam sem limite por IP.");
        }
    }

    @Override
    public int getOrder() {
        return -400;   // depois do X-Request-Id (-500) e do registro de acesso (-490), antes da segurança (-100)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        if (limitePorMinuto <= 0 || !PUBLICAS.matches(troca.getRequest().getPath().pathWithinApplication())) {
            return cadeia.filter(troca);
        }
        long agora = relogio.millis();
        long minuto = agora / MINUTO;
        esquecerJanelasPassadas(minuto);

        Janela janela = janelas.compute(ip(troca), (chave, atual) ->
                atual == null || atual.minuto() != minuto ? new Janela(minuto, new AtomicInteger()) : atual);
        if (janela.contagem().incrementAndGet() <= limitePorMinuto) {
            return cadeia.filter(troca);
        }
        long segundosAteAProxima = Math.max(1, ((minuto + 1) * MINUTO - agora + 999) / 1000);
        troca.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(segundosAteAProxima));
        return erros.escrever(troca.getResponse(), 429, "Muitas requisições. Tente de novo em instantes.");
    }

    private static String ip(ServerWebExchange troca) {
        InetSocketAddress remoto = troca.getRequest().getRemoteAddress();
        return remoto == null || remoto.getAddress() == null ? "desconhecido" : remoto.getAddress().getHostAddress();
    }

    /** Uma vez por minuto: o mapa guarda só os IPs do minuto corrente. */
    private void esquecerJanelasPassadas(long minuto) {
        if (ultimaLimpeza < minuto) {
            ultimaLimpeza = minuto;
            janelas.values().removeIf(janela -> janela.minuto() < minuto);
        }
    }
}
