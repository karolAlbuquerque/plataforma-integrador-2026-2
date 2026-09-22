package br.com.plataforma.identity.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * O identity emite os tokens e também os valida, como qualquer módulo, com o JwtDecoder de
 * {@link ChavesJwt}. As rotas de sessão são públicas: quem chega nelas ainda não tem token.
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    private static final String[] ROTAS_PUBLICAS = {
            "/api/identity/auth/login",
            "/api/identity/auth/refresh",
            "/api/identity/auth/logout",
            "/api/identity/auth/token-servico",
            "/api/identity/auth/senha/recuperar",
            "/api/identity/auth/senha/verificar",
            "/api/identity/auth/senha/definir",
            "/api/identity/.well-known/jwks.json",
            "/api/identity/health",
            "/error"
    };

    @Bean
    SecurityFilterChain filtrosDeSeguranca(HttpSecurity http, ObjectMapper mapper) throws Exception {
        EscritorDeErro erros = new EscritorDeErro(mapper);
        AuthenticationEntryPoint naoAutenticado =
                (requisicao, resposta, excecao) -> erros.escrever(resposta, 401, "Autenticação necessária.");
        AccessDeniedHandler semPermissao =
                (requisicao, resposta, excecao) -> erros.escrever(resposta, 403, "Sem permissão para esta operação.");

        http
                // O access token vai no cabeçalho. O cookie de refresh é SameSite=Strict e restrito a
                // /api/identity/auth: o navegador não o envia em requisição vinda de outro site.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(permissoesDoToken()))
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(semPermissao))
                .addFilterAfter(new TenantFiltro(erros), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /** Cada item do claim "perms" vira uma authority, sem prefixo: hasAuthority('identity.timeline.ver'). */
    static JwtAuthenticationConverter permissoesDoToken() {
        JwtGrantedAuthoritiesConverter permissoes = new JwtGrantedAuthoritiesConverter();
        permissoes.setAuthoritiesClaimName("perms");
        permissoes.setAuthorityPrefix("");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(permissoes);
        return conversor;
    }

    /** BCrypt com custo 12 para senha de usuário e segredo de serviço (Modelo §5). */
    @Bean
    PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder(12);
    }
}
