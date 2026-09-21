package br.com.plataforma.gateway;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Um domínio só, separado por caminho (Contrato §12.8, decisão D14):
 *
 * <pre>
 *   /api/identity/**                       identity
 *   /api/{modulo}/** e /public/{modulo}/**  API do módulo      (ROTA_{MODULO}_API)
 *   /modulos/{modulo}/**                   front do módulo    (ROTA_{MODULO}_FRONT)
 *   todo o resto                           casca              (ROTA_CASCA)
 * </pre>
 *
 * /api, /public e /modulos sem rota respondem 404 — nunca caem na casca, que devolveria o
 * index.html com 200 para uma chamada de API.
 */
@Configuration
public class Rotas {

    private static final Logger log = LoggerFactory.getLogger(Rotas.class);

    @Bean
    RouteLocator rotasDaPlataforma(RouteLocatorBuilder construtor, Environment ambiente,
                       @Value("${gateway.modulos}") List<String> modulos) {
        RouteLocatorBuilder.Builder rotas = construtor.routes()
                .route("identity-api", r -> r.path("/api/identity/**").uri(exigir(ambiente, "ROTA_IDENTITY_API")));

        for (String modulo : modulos) {
            String codigo = modulo.strip();
            String variavel = "ROTA_" + codigo.toUpperCase(Locale.ROOT);
            String api = ambiente.getProperty(variavel + "_API");
            if (api != null && !api.isBlank()) {
                rotas.route(codigo + "-api", r -> r.path("/api/" + codigo + "/**", "/public/" + codigo + "/**").uri(api));
            }
            String front = ambiente.getProperty(variavel + "_FRONT");
            if (front != null && !front.isBlank()) {
                rotas.route(codigo + "-front", r -> r.path("/modulos/" + codigo + "/**").uri(front));
            }
            if (api == null && front == null) {
                log.info("Módulo {} sem {}_API nem {}_FRONT: sem rota.", codigo, variavel, variavel);
            }
        }

        String casca = exigir(ambiente, "ROTA_CASCA");
        rotas.route("casca", r -> r.order(Ordered.LOWEST_PRECEDENCE)
                .path("/**")
                .and().not(p -> p.path("/api/**", "/public/**", "/modulos/**"))
                .uri(casca));
        return rotas.build();
    }

    private static String exigir(Environment ambiente, String variavel) {
        String valor = ambiente.getProperty(variavel);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável " + variavel + " não definida. Confira o docker-compose do infra.");
        }
        return valor;
    }
}
