package br.com.plataforma.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import com.sun.net.httpserver.HttpServer;

/**
 * Rotas, segurança e erros do gateway, sem nenhum outro serviço no ar. Um servidor de eco faz o
 * papel do módulo "exemplo" e devolve os cabeçalhos que recebeu; o CRM aponta para uma porta
 * fechada, como um módulo fora do ar.
 *
 * Sem @AutoConfigureWebTestClient de propósito: com ele o cliente fala direto com o contexto, sem
 * HTTP, e a requisição chega sem endereço remoto — o X-Forwarded-For não teria o que gravar.
 */
// Os testes do Spring Boot desligam a exportação de métricas; aqui ela é o que se testa
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    private static final HttpServer ECO = iniciarEco();

    @Autowired
    WebTestClient cliente;

    @LocalManagementPort
    int gerencia;

    @DynamicPropertySource
    static void rotas(DynamicPropertyRegistry registro) {
        String eco = "http://localhost:" + ECO.getAddress().getPort();
        registro.add("ROTA_CASCA", () -> eco);
        registro.add("ROTA_IDENTITY_API", () -> eco);
        registro.add("ROTA_EXEMPLO_API", () -> eco);
        registro.add("ROTA_EXEMPLO_FRONT", () -> eco);
        registro.add("ROTA_CRM_API", () -> "http://localhost:1");
        // Como um módulo fora do compose: o nome nem existe na rede
        registro.add("ROTA_FINANCEIRO_API", () -> "http://modulo-fora-do-compose.invalid:8085");
        registro.add("JWKS_URI", () -> "http://localhost:1/jwks.json");
        // Duas origens válidas, com espaço e barra no fim; "*" e origem com caminho são ignoradas
        registro.add("CORS_ORIGENS", () -> " http://localhost:5173/, http://localhost:3002,*,http://localhost:3003/app");
    }

    @AfterAll
    static void pararEco() {
        ECO.stop(0);
    }

    @Test
    void apiSemTokenResponde401NoEnvelope() {
        cliente.get().uri("/api/crm/empresas").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(CorrelacaoFiltro.CABECALHO)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Autenticação necessária.");
    }

    @Test
    void tokenInvalidoResponde401() {
        cliente.get().uri("/api/exemplo/itens").header("Authorization", "Bearer nao-e-um-jwt").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void moduloForaDoArResponde503ComOCodigoDoModulo() {
        cliente.get().uri("/api/crm/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Módulo crm indisponível.")
                .jsonPath("$.errors[0].campo").isEqualTo("modulo")
                .jsonPath("$.errors[0].codigo").isEqualTo("MODULO_INDISPONIVEL")
                .jsonPath("$.errors[0].detalhe").isEqualTo("crm");
    }

    @Test
    void moduloSemNomeNaRedeResponde503AntesDoTempoLimite() {
        long inicio = System.nanoTime();
        cliente.get().uri("/api/financeiro/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errors[0].codigo").isEqualTo("MODULO_INDISPONIVEL")
                .jsonPath("$.errors[0].detalhe").isEqualTo("financeiro");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void moduloQueNaoRespondeEm3SegundosResponde504() {
        long inicio = System.nanoTime();
        cliente.get().uri("/public/exemplo/lento").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Módulo exemplo não respondeu a tempo.")
                .jsonPath("$.errors[0].codigo").isEqualTo("MODULO_SEM_RESPOSTA")
                .jsonPath("$.errors[0].detalhe").isEqualTo("exemplo");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void rotasDeSessaoAbertaExigemTokenJaNoGateway() {
        for (String rota : new String[] {"/api/identity/auth/me", "/api/identity/auth/sessoes", "/api/identity/auth/senha",
                "/api/identity/auth/qualquer-outra", "/api/identity/usuarios",
                "/api/identity/auth/login/segundo-fator/outra"}) {
            cliente.post().uri(rota).exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.message").isEqualTo("Autenticação necessária.");
        }
        for (String rota : new String[] {"/api/identity/auth/refresh", "/api/identity/auth/logout",
                "/api/identity/auth/token-servico", "/api/identity/auth/senha/recuperar",
                "/api/identity/auth/senha/verificar", "/api/identity/auth/senha/definir",
                "/api/identity/auth/login/segundo-fator", "/api/identity/auth/login/segundo-fator/cadastro",
                "/api/identity/auth/login/segundo-fator/cadastro/confirmar"}) {
            cliente.post().uri(rota).exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=" + rota));
        }
        cliente.get().uri("/api/identity/.well-known/jwks.json").exchange().expectStatus().isOk();
    }

    @Test
    void corsLiberaSoAsOrigensConfiguradas() {
        cliente.options().uri("/api/exemplo/itens")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange()
                .expectStatus().isOk()   // o preflight não leva token e não pode dar 401
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");

        cliente.get().uri("/api/exemplo/health").header("Origin", "http://localhost:3002").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3002")
                .expectHeader().value("Access-Control-Expose-Headers", valor -> assertThat(valor).contains("X-Request-Id"));

        for (String origem : new String[] {"http://site-malicioso.com", "http://localhost:3003", "http://qualquer.com"}) {
            cliente.options().uri("/api/exemplo/itens")
                    .header("Origin", origem)
                    .header("Access-Control-Request-Method", "POST")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist("Access-Control-Allow-Origin");
        }
    }

    @Test
    void cabecalhoDeAte32KbPassaEAcimaDissoERecusado() {
        // Contrato §4.4: o token de um ADMINISTRADOR passa de 8 KB, o padrão do Netty
        cliente.get().uri("/api/exemplo/health").header("X-Preenchimento", "a".repeat(28_000)).exchange()
                .expectStatus().isOk();
        cliente.get().uri("/api/exemplo/health").header("X-Preenchimento", "a".repeat(40_000)).exchange()
                .expectStatus().value(status -> assertThat(status).isIn(400, 431));
    }

    @Test
    void semCorsOrigensNaoHaCors() {
        CorsConfigurationSource desligado = new SegurancaConfig().origensLiberadas("");
        MockServerWebExchange preflight = MockServerWebExchange.from(MockServerHttpRequest.options("/api/crm/empresas")
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"));
        assertThat(desligado.getCorsConfiguration(preflight)).isNull();

        assertThat(SegurancaConfig.origensValidas("http://localhost:5173, https://app.exemplo.com:8443/ ,*, localhost:3002,"
                + "http://localhost:3004/caminho, ftp://x.com"))
                .containsExactly("http://localhost:5173", "https://app.exemplo.com:8443");
    }

    @Test
    void rotaPublicaChegaAoModuloComXRequestIdESemXTenantId() {
        String corpo = cliente.get().uri("/api/exemplo/health")
                .header("X-Tenant-Id", "a0000000-0000-4000-8000-00000000000a")
                .header("X-Forwarded-For", "9.9.9.9")   // inventado pelo cliente: não pode chegar ao módulo
                .header(CorrelacaoFiltro.CABECALHO, "req-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelacaoFiltro.CABECALHO, "req-123")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(corpo).contains("path=/api/exemplo/health", "x-request-id=req-123")
                .containsPattern("x-forwarded-for=(127\\.0\\.0\\.1|0:0:0:0:0:0:0:1)\\n")
                .doesNotContain("x-tenant-id", "9.9.9.9");
    }

    @Test
    void xRequestIdComCaracteresEstranhosETrocado() {
        cliente.get().uri("/api/exemplo/health").header(CorrelacaoFiltro.CABECALHO, "id com espaco").exchange()
                .expectHeader().value(CorrelacaoFiltro.CABECALHO, id -> assertThat(id).hasSize(36));
    }

    @Test
    void frontDoModuloECascaSaoPublicos() {
        cliente.get().uri("/modulos/exemplo/").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Frame-Options", "SAMEORIGIN")   // a casca embute na mesma origem
                .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=/modulos/exemplo/"));
        cliente.get().uri("/app/exemplo/itens").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=/app/exemplo/itens"));
    }

    @Test
    void modulosSemRotaNaoCaemNaCasca() {
        cliente.get().uri("/modulos/financeiro/").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("Rota não encontrada.");
    }

    @Test
    void metricasSaoDaPortaDeGerenciaENaoDaBorda() {
        // Uma chamada roteada antes, para existir a métrica do gateway
        cliente.get().uri("/api/exemplo/health").exchange().expectStatus().isOk();

        WebTestClient.bindToServer().baseUrl("http://localhost:" + gerencia).build()
                .get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(corpo -> assertThat(corpo)
                        .contains("application=\"gateway\"", "http_server_requests_seconds_count"));
        // Na 8080, /actuator é só mais um caminho, que vai para a casca
        cliente.get().uri("/actuator/prometheus").exchange()
                .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=/actuator/prometheus"));
    }

    @Test
    void loginEPublicoEChegaAoIdentity() {
        cliente.post().uri("/api/identity/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=/api/identity/auth/login"));
    }

    /**
     * Responde 200 com o caminho e os cabeçalhos recebidos, em minúsculas. Caminho terminado em
     * "/lento" demora 5 segundos, como um módulo travado.
     */
    private static HttpServer iniciarEco() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool());   // o lento não pode segurar os outros
            servidor.createContext("/", troca -> {
                if (troca.getRequestURI().getPath().endsWith("/lento")) {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String cabecalhos = troca.getRequestHeaders().entrySet().stream()
                        .map(Map.Entry::getKey)
                        .map(nome -> nome.toLowerCase() + "=" + troca.getRequestHeaders().getFirst(nome))
                        .collect(Collectors.joining("\n"));
                byte[] corpo = ("path=" + troca.getRequestURI().getPath() + "\n" + cabecalhos).getBytes(StandardCharsets.UTF_8);
                troca.sendResponseHeaders(200, corpo.length);
                try (OutputStream saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
