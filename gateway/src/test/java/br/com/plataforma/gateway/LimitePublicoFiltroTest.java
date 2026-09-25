package br.com.plataforma.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/** Requisito RF45: limite por IP em /public/**, com relógio fixo para a janela não virar no meio. */
class LimitePublicoFiltroTest {

    /** 10h00min15s: faltam 45 segundos para a próxima janela. */
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-22T13:00:15Z"), ZoneOffset.UTC);

    private final LimitePublicoFiltro filtro = new LimitePublicoFiltro(3, new EscritorDeErro(new ObjectMapper()), RELOGIO);

    @Test
    void passaDoLimiteResponde429NoEnvelopeComRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(chamar("/public/landing/formularios/abc", "203.0.113.7").getStatusCode()).isNull();
        }

        MockServerWebExchange quarta = troca("/public/landing/formularios/abc", "203.0.113.7");
        filtro.filter(quarta, t -> Mono.empty()).block();

        assertThat(quarta.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(quarta.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("45");
        assertThat(quarta.getResponse().getBodyAsString().block())
                .contains("\"success\":false", "Muitas requisições. Tente de novo em instantes.");
    }

    @Test
    void cadaIpTemASuaContaEAsRotasAutenticadasNaoEntram() {
        for (int i = 0; i < 3; i++) {
            chamar("/public/landing/x", "203.0.113.8");
        }
        assertThat(chamar("/public/landing/x", "203.0.113.9").getStatusCode()).isNull();
        for (int i = 0; i < 10; i++) {
            assertThat(chamar("/api/crm/empresas", "203.0.113.8").getStatusCode()).isNull();
            assertThat(chamar("/modulos/landing/", "203.0.113.8").getStatusCode()).isNull();
        }
    }

    @Test
    void casaOCaminhoComoAsRotas() {
        // O filtro e a rota /public/{m}/** usam o mesmo PathPattern: /%70ublic/ não é rota pública
        // (vai para a casca, como qualquer caminho desconhecido), e por isso também não conta aqui
        for (int i = 0; i < 3; i++) {
            chamar("/public/landing/x", "203.0.113.10");
        }
        assertThat(chamar("/%70ublic/landing/x", "203.0.113.10").getStatusCode()).isNull();
        assertThat(chamar("/public/landing/../landing/x", "203.0.113.10").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void limiteZeroDesliga() {
        LimitePublicoFiltro desligado = new LimitePublicoFiltro(0, new EscritorDeErro(new ObjectMapper()), RELOGIO);
        for (int i = 0; i < 20; i++) {
            MockServerWebExchange troca = troca("/public/landing/x", "203.0.113.11");
            desligado.filter(troca, t -> Mono.empty()).block();
            assertThat(troca.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    void moduloDoRegistroDeAcesso() {
        assertThat(RegistroDeAcessoFiltro.moduloDe("/api/crm/empresas")).isEqualTo("crm");
        assertThat(RegistroDeAcessoFiltro.moduloDe("/public/landing/p/abc")).isEqualTo("landing");
        assertThat(RegistroDeAcessoFiltro.moduloDe("/api/identity")).isEqualTo("identity");
        assertThat(RegistroDeAcessoFiltro.moduloDe("/api/Nao-Modulo/x")).isEqualTo("?");
        assertThat(RegistroDeAcessoFiltro.moduloDe("/modulos/crm/")).isNull();
        assertThat(RegistroDeAcessoFiltro.moduloDe("/")).isNull();
    }

    private MockServerHttpResponse chamar(String caminho, String ip) {
        MockServerWebExchange troca = troca(caminho, ip);
        filtro.filter(troca, t -> Mono.empty()).block();
        return troca.getResponse();
    }

    private static MockServerWebExchange troca(String caminho, String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(caminho)
                .remoteAddress(new InetSocketAddress(ip, 50_000)));
    }
}
