package br.com.plataforma.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Uma linha de log por chamada de API (Requisitos RF47 e RNF04): método, caminho, status, duração,
 * módulo e X-Request-Id — o mesmo id que o módulo grava no log dele. No log estruturado (ECS) os
 * campos saem separados; em texto, na própria mensagem. Arquivos estáticos da casca e dos fronts
 * ficam de fora, e a query string também, porque pode trazer dado pessoal.
 *
 * Registra no momento de enviar a resposta, e não no fim da cadeia: o 503 e o 504 são escritos
 * pelo TratadorDeErros depois de a cadeia terminar com erro.
 */
@Component
public class RegistroDeAcessoFiltro implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("br.com.plataforma.gateway.acesso");

    @Override
    public int getOrder() {
        return -490;   // logo depois do X-Request-Id (-500), para registrar também o 401 e o 429
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        String completo = troca.getRequest().getPath().pathWithinApplication().value();
        String modulo = moduloDe(completo);
        String caminho = completo.length() > 300 ? completo.substring(0, 300) + "…" : completo;
        if (modulo == null) {
            return cadeia.filter(troca);
        }
        long inicio = System.nanoTime();
        troca.getResponse().beforeCommit(() -> {
            HttpStatusCode status = troca.getResponse().getStatusCode();
            int codigo = status == null ? 200 : status.value();
            long duracao = (System.nanoTime() - inicio) / 1_000_000;
            String metodo = troca.getRequest().getMethod().name();
            String id = troca.getRequest().getHeaders().getFirst(CorrelacaoFiltro.CABECALHO);
            log.atInfo()
                    .addKeyValue("requestId", id)
                    .addKeyValue("metodo", metodo)
                    .addKeyValue("caminho", caminho)
                    .addKeyValue("modulo", modulo)
                    .addKeyValue("status", codigo)
                    .addKeyValue("duracaoMs", duracao)
                    .log("{} {} {} {} ms [{}]", metodo, caminho, codigo, duracao, id);
            return Mono.empty();
        });
        return cadeia.filter(troca);
    }

    /** "crm" em /api/crm/... e em /public/crm/...; nulo fora das APIs. */
    static String moduloDe(String caminho) {
        String resto = caminho.startsWith("/api/") ? caminho.substring(5)
                : caminho.startsWith("/public/") ? caminho.substring(8)
                : null;
        if (resto == null || resto.isEmpty()) {
            return null;
        }
        int barra = resto.indexOf('/');
        String modulo = barra < 0 ? resto : resto.substring(0, barra);
        return modulo.matches("[a-z]{1,30}") ? modulo : "?";
    }
}
