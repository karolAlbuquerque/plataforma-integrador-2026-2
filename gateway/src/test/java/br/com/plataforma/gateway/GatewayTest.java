package br.com.plataforma.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpServer;

/**
 * Rotas, segurança e erros do gateway, sem nenhum outro serviço no ar. Um servidor de eco faz o
 * papel do módulo "exemplo" e devolve os cabeçalhos que recebeu; o CRM aponta para uma porta
 * fechada, como um módulo fora do ar.
 *
 * Sem @AutoConfigureWebTestClient de propósito: com ele o cliente fala direto com o contexto, sem
 * HTTP, e a requisição chega sem endereço remoto — o X-Forwarded-For não teria o que gravar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    private static final HttpServer ECO = iniciarEco();

    @Autowired
    WebTestClient cliente;

    @DynamicPropertySource
    static void rotas(DynamicPropertyRegistry registro) {
        String eco = "http://localhost:" + ECO.getAddress().getPort();
        registro.add("ROTA_CASCA", () -> eco);
        registro.add("ROTA_IDENTITY_API", () -> eco);
        registro.add("ROTA_EXEMPLO_API", () -> eco);
        registro.add("ROTA_EXEMPLO_FRONT", () -> eco);
        registro.add("ROTA_CRM_API", () -> "http://localhost:1");
        registro.add("JWKS_URI", () -> "http://localhost:1/jwks.json");
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
    void moduloForaDoArResponde503NoEnvelope() {
        cliente.get().uri("/api/crm/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Módulo crm indisponível.");
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
    void loginEPublicoEChegaAoIdentity() {
        cliente.post().uri("/api/identity/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(corpo -> assertThat(corpo).contains("path=/api/identity/auth/login"));
    }

    /** Responde 200 com o caminho e os cabeçalhos recebidos, em minúsculas. */
    private static HttpServer iniciarEco() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", troca -> {
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
