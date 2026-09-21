package br.com.plataforma.gateway;

import java.net.InetSocketAddress;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * O gateway é a borda: tudo o que chega nele vem do navegador (decisão D6). Cabeçalhos que o
 * navegador poderia inventar são reescritos antes de seguir para o módulo:
 *
 * <ul>
 *   <li>X-Forwarded-For passa a ser só o IP da conexão TCP. O identity usa esse IP no limite de
 *       tentativas de login; se o cliente pudesse escolhê-lo, trocaria de "IP" a cada tentativa.
 *       Os filtros nativos de X-Forwarded do Spring Cloud Gateway ficam desligados (sem
 *       trusted-proxies no application.yml) justamente para não aceitarem o valor do cliente.</li>
 *   <li>X-Tenant-Id é removido. Ele só vale com token de serviço, e serviço fala com serviço direto
 *       pela rede do Docker, nunca pelo gateway. No navegador, o tenant vem do token.</li>
 * </ul>
 */
@Component
public class CabecalhosDoNavegadorFiltro implements WebFilter, Ordered {

    static final String ENCAMINHADO_PARA = "X-Forwarded-For";
    static final String TENANT = "X-Tenant-Id";

    @Override
    public int getOrder() {
        return -400;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        InetSocketAddress remoto = troca.getRequest().getRemoteAddress();
        String ip = remoto != null && remoto.getAddress() != null ? remoto.getAddress().getHostAddress() : null;

        ServerWebExchange reescrita = troca.mutate().request(r -> r.headers(cabecalhos -> {
            cabecalhos.remove(TENANT);
            cabecalhos.remove("Forwarded");
            cabecalhos.keySet().removeIf(nome -> nome.regionMatches(true, 0, "X-Forwarded-", 0, 12));
            if (ip != null) {
                cabecalhos.set(ENCAMINHADO_PARA, ip);
            }
        })).build();
        return cadeia.filter(reescrita);
    }
}
