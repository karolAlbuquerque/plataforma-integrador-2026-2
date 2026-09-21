package br.com.plataforma.gateway;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * X-Request-Id em toda requisição (Requisito RF47): gera se faltar, repassa ao módulo e devolve
 * ao navegador. É um WebFilter, e não um filtro de rota, para valer também no 401 da segurança
 * e no 404 de rota inexistente.
 */
@Component
public class CorrelacaoFiltro implements WebFilter, Ordered {

    public static final String CABECALHO = "X-Request-Id";

    /** Só o que é seguro repetir em log; o resto é trocado por um id novo. */
    private static final Pattern ID_ACEITO = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    @Override
    public int getOrder() {
        return -500;   // antes da cadeia do Spring Security (-100)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        String recebido = troca.getRequest().getHeaders().getFirst(CABECALHO);
        String id = recebido != null && ID_ACEITO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();

        ServerWebExchange comId = troca.mutate().request(r -> r.headers(h -> h.set(CABECALHO, id))).build();
        comId.getResponse().beforeCommit(() -> {
            comId.getResponse().getHeaders().set(CABECALHO, id);   // sobrescreve o que o módulo devolveu
            return Mono.empty();
        });
        return cadeia.filter(comId);
    }
}
