package br.com.plataforma.gateway;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Primeira das duas camadas de validação do token (Requisito RF43): toda chamada a /api exige
 * JWT válido, com a chave do JWKS do identity e audiência "plataforma". O módulo valida de novo.
 *
 * Sem token passa só o que se usa antes de haver sessão — entrar, renovar, sair, o token de
 * serviço e os três passos do link de senha —, além do JWKS, dos healthchecks, das rotas /public
 * dos módulos (RF44) e de tudo o que não é API: casca e fronts são arquivos estáticos. O resto de
 * /api/identity/auth (me, sessões, troca de senha) exige token aqui também.
 */
@Configuration
@EnableWebFluxSecurity
public class SegurancaConfig {

    private static final Logger log = LoggerFactory.getLogger(SegurancaConfig.class);

    static final String[] SEM_TOKEN = {
            "/api/identity/auth/login",
            "/api/identity/auth/refresh",
            "/api/identity/auth/logout",
            "/api/identity/auth/token-servico",
            "/api/identity/auth/senha/recuperar",
            "/api/identity/auth/senha/verificar",
            "/api/identity/auth/senha/definir",
            "/api/identity/.well-known/**",
            "/api/*/health"
    };

    /** Só esquema, host e porta: é assim que o navegador escreve o Origin. */
    private static final Pattern ORIGEM = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$");

    @Bean
    SecurityWebFilterChain filtrosDeSeguranca(ServerHttpSecurity http, EscritorDeErro erros) {
        ServerAuthenticationEntryPoint naoAutenticado =
                (troca, excecao) -> erros.escrever(troca.getResponse(), 401, "Autenticação necessária.");

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(Customizer.withDefaults())
                // Os fronts dos módulos vivem em iframe na casca, na mesma origem (D1, D14). O padrão
                // DENY do Spring Security, somado a toda resposta que passa pelo gateway, impediria a
                // casca de embutir o front de um módulo que não mande o próprio frame-ancestors.
                .headers(cabecalhos -> cabecalhos.frameOptions(quadros -> quadros.mode(Mode.SAMEORIGIN)))
                .authorizeExchange(regras -> regras
                        .pathMatchers(SEM_TOKEN).permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(naoAutenticado))
                .exceptionHandling(e -> e.authenticationEntryPoint(naoAutenticado));

        return http.build();
    }

    /**
     * CORS só existe em desenvolvimento, para o front de um módulo rodando sozinho no Vite
     * (Requisito RF46): em produção casca, fronts e APIs têm a mesma origem. CORS_ORIGENS vazio
     * desliga — sem configuração, o navegador bloqueia qualquer chamada de outra origem. Nenhum
     * módulo configura CORS.
     */
    @Bean
    CorsConfigurationSource origensLiberadas(@Value("${gateway.cors-origens:}") String origens) {
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        List<String> validas = origensValidas(origens);
        if (validas.isEmpty()) {
            return fonte;
        }
        CorsConfiguration liberacao = new CorsConfiguration();
        liberacao.setAllowedOrigins(validas);
        liberacao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        liberacao.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelacaoFiltro.CABECALHO));
        liberacao.setExposedHeaders(List.of(CorrelacaoFiltro.CABECALHO, "Location", "Retry-After"));
        liberacao.setAllowCredentials(true);   // o cookie de refresh do login feito pelo front local
        liberacao.setMaxAge(Duration.ofHours(1));
        fonte.registerCorsConfiguration("/api/**", liberacao);
        fonte.registerCorsConfiguration("/public/**", liberacao);
        log.info("CORS liberado para {}.", validas);
        return fonte;
    }

    /** "*" não entra: com credenciais, liberar todas as origens entregaria a sessão a qualquer site. */
    static List<String> origensValidas(String origens) {
        List<String> validas = new ArrayList<>();
        for (String origem : origens == null ? new String[0] : origens.split(",")) {
            String limpa = origem.strip().replaceAll("/+$", "");
            if (limpa.isEmpty()) {
                continue;
            }
            if (ORIGEM.matcher(limpa).matches() && URI.create(limpa).getPort() <= 65535) {
                validas.add(limpa);
            } else {
                log.error("CORS_ORIGENS: '{}' ignorada; use esquema, host e porta, como http://localhost:5173.", limpa);
            }
        }
        return validas;
    }
}
