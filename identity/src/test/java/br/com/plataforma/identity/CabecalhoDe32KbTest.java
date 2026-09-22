package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Contrato §4.4 e Requisito RF02: o token de um ADMINISTRADOR com o catálogo inteiro passa de
 * 8 KB, o limite padrão do Tomcat. Todo serviço aceita cabeçalho de até 32 KB — aqui com o Tomcat
 * de verdade, que o MockMvc dos outros testes não tem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CabecalhoDe32KbTest extends BaseIntegracao {

    @LocalServerPort
    int porta;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void cabecalhosDeAte32KbPassamEAcimaDissoSaoRecusados() throws Exception {
        String token = tokenDe("administrador@empresa-a.dev");

        // ~28 KB entre token e cabeçalho extra: dentro do limite
        HttpResponse<String> dentro = cliente.send(requisicao(token, 28_000), HttpResponse.BodyHandlers.ofString());
        assertThat(dentro.statusCode()).isEqualTo(200);
        assertThat(dentro.body()).contains("\"email\":\"administrador@empresa-a.dev\"");

        // ~40 KB: o Tomcat recusa antes de chegar à aplicação
        HttpResponse<String> fora = cliente.send(requisicao(token, 40_000), HttpResponse.BodyHandlers.ofString());
        assertThat(fora.statusCode()).isEqualTo(400);
    }

    private HttpRequest requisicao(String token, int tamanhoDoExtra) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/identity/auth/me"))
                .header("Authorization", bearer(token))
                .header("X-Preenchimento", "a".repeat(tamanhoDoExtra))
                .GET()
                .build();
    }
}
