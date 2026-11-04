package br.com.plataforma.gateway;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 *   <li>X-Forwarded-For passa a ser só o IP do cliente (da conexão TCP em desenvolvimento, ou a
 *       última entrada acrescentada pelo proxy reverso quando {@code CONFIAR_PROXY=true} e a porta
 *       do gateway está fechada para fora). O identity e o LimitePublicoFiltro usam esse IP; se o
 *       cliente pudesse escolhê-lo, trocaria de "IP" a cada tentativa.</li>
 *   <li>X-Tenant-Id é removido. Ele só vale com token de serviço, e serviço fala com serviço direto
 *       pela rede do Docker, nunca pelo gateway. No navegador, o tenant vem do token.</li>
 * </ul>
 */
@Component
public class CabecalhosDoNavegadorFiltro implements WebFilter, Ordered {

    static final String ENCAMINHADO_PARA = "X-Forwarded-For";
    static final String TENANT = "X-Tenant-Id";

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    private static final Pattern CARACTERES_IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final boolean confiarProxy;

    @Autowired
    public CabecalhosDoNavegadorFiltro(@Value("${gateway.confiar-proxy:false}") boolean confiarProxy) {
        this.confiarProxy = confiarProxy;
    }

    @Override
    public int getOrder() {
        return -450;   // antes do LimitePublicoFiltro (-400), que lê o IP já saneado
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        String ip = resolverIp(troca);

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

    private String resolverIp(ServerWebExchange troca) {
        if (confiarProxy) {
            String encaminhado = troca.getRequest().getHeaders().getFirst(ENCAMINHADO_PARA);
            if (encaminhado != null && !encaminhado.isBlank()) {
                String[] entradas = encaminhado.split(",");
                String ultima = normalizarIp(entradas[entradas.length - 1].strip());
                if (ultima != null) {
                    return ultima;
                }
            }
        }
        InetSocketAddress remoto = troca.getRequest().getRemoteAddress();
        return remoto != null && remoto.getAddress() != null ? remoto.getAddress().getHostAddress() : null;
    }

    /** Aceita só IP literal; nunca resolve nome (sem consulta de DNS). Inválido vira nulo. */
    static String normalizarIp(String valor) {
        if (valor == null) {
            return null;
        }
        if (IPV4.matcher(valor).matches()) {
            return valor;
        }
        if (valor.contains(":") && CARACTERES_IPV6.matcher(valor).matches()) {
            try {
                return InetAddress.getByName(valor).getHostAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }
}
