package br.com.plataforma.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

/**
 * Primeira das duas camadas de validação do token (Requisito RF43): toda chamada a /api exige
 * JWT válido, com a chave do JWKS do identity e audiência "plataforma". O módulo valida de novo.
 *
 * Público: login e renovação (quem chega ainda não tem token), o JWKS, os healthchecks, as rotas
 * /public dos módulos e tudo o que não é API — casca e fronts são arquivos estáticos.
 */
@Configuration
@EnableWebFluxSecurity
public class SegurancaConfig {

    @Bean
    SecurityWebFilterChain filtrosDeSeguranca(ServerHttpSecurity http, EscritorDeErro erros) {
        ServerAuthenticationEntryPoint naoAutenticado =
                (troca, excecao) -> erros.escrever(troca.getResponse(), 401, "Autenticação necessária.");

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(regras -> regras
                        .pathMatchers("/api/identity/auth/**", "/api/identity/.well-known/**", "/api/*/health").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(naoAutenticado))
                .exceptionHandling(e -> e.authenticationEntryPoint(naoAutenticado));

        return http.build();
    }
}
