package br.com.plataforma.identity.modulo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consulta o healthcheck de cada módulo, pelo gateway, e guarda o resultado em memória. A casca
 * marca como indisponível o módulo que falhou, em vez de abrir um iframe quebrado (RF35).
 * Módulo nunca consultado conta como disponível.
 */
@Component
public class SaudeDosModulos {

    private static final Logger log = LoggerFactory.getLogger(SaudeDosModulos.class);

    private final ModuloRepositorio modulos;
    private final boolean habilitada;
    private final String urlBase;
    private final Duration timeout;
    private final HttpClient cliente;
    private final Map<String, Boolean> estado = new ConcurrentHashMap<>();

    public SaudeDosModulos(ModuloRepositorio modulos,
                           @Value("${identity.saude.habilitada}") boolean habilitada,
                           @Value("${identity.saude.base-url}") String urlBase,
                           @Value("${identity.saude.timeout}") Duration timeout) {
        this.modulos = modulos;
        this.habilitada = habilitada;
        this.urlBase = urlBase;
        this.timeout = timeout;
        this.cliente = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean disponivel(String codigo) {
        return estado.getOrDefault(codigo, true);
    }

    @Scheduled(initialDelayString = "${identity.saude.intervalo}", fixedDelayString = "${identity.saude.intervalo}")
    public void verificar() {
        if (!habilitada) {
            return;
        }
        List<ModuloRepositorio.Modulo> ativos = modulos.ativos();
        // Todos em paralelo: um módulo lento não atrasa a verificação dos outros
        Map<String, CompletableFuture<Boolean>> consultas = new ConcurrentHashMap<>();
        ativos.forEach(modulo -> consultas.put(modulo.codigo(), consultar(modulo.healthcheck())));
        consultas.forEach((codigo, consulta) -> {
            boolean disponivel = consulta.join();
            Boolean anterior = estado.put(codigo, disponivel);
            if (anterior == null || anterior != disponivel) {
                log.info("Módulo {} {}.", codigo, disponivel ? "disponível" : "indisponível");
            }
        });
    }

    private CompletableFuture<Boolean> consultar(String caminho) {
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(urlBase + caminho)).timeout(timeout).GET().build();
            return cliente.sendAsync(pedido, HttpResponse.BodyHandlers.discarding())
                    .thenApply(resposta -> resposta.statusCode() >= 200 && resposta.statusCode() < 300)
                    .exceptionally(erro -> false);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
