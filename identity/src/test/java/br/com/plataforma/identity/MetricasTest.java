package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Requisito RNF04: métricas de erro e de latência no formato do Prometheus, só na porta de
 * gerência — a que o gateway não encaminha e o compose não publica.
 */
// Os testes do Spring Boot desligam a exportação de métricas; aqui ela é o que se testa
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricasTest extends BaseIntegracao {

    @LocalServerPort
    int porta;

    @LocalManagementPort
    int gerencia;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void prometheusRespondeNaPortaDeGerenciaESoNela() throws Exception {
        assertThat(obter(porta, "/api/identity/health").statusCode()).isEqualTo(200);

        HttpResponse<String> metricas = obter(gerencia, "/actuator/prometheus");
        assertThat(metricas.statusCode()).isEqualTo(200);
        assertThat(metricas.body()).contains("http_server_requests_seconds_count", "application=\"identity\"");

        assertThat(obter(porta, "/actuator/prometheus").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> obter(int portaDaChamada, String caminho) throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + portaDaChamada + caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
