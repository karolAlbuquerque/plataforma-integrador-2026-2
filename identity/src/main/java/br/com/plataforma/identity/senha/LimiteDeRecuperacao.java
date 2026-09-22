package br.com.plataforma.identity.senha;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pedidos de recuperação por IP, em memória. A rota é pública e dispara e-mail: sem limite, vira
 * ferramenta para encher a caixa de alguém. Em memória basta — o identity roda numa instância só
 * no semestre, e perder a contagem num reinício não abre brecha relevante. O limite geral das
 * rotas públicas (RF45) fica para a onda 3.
 */
@Component
class LimiteDeRecuperacao {

    private static final Duration JANELA = Duration.ofHours(1);
    private static final int MAXIMO_DE_IPS = 10_000;

    private final int maximoPorIp;
    private final Map<String, Deque<Instant>> pedidos = new HashMap<>();

    LimiteDeRecuperacao(@Value("${identity.senha.recuperacoes-por-ip-por-hora}") int maximoPorIp) {
        this.maximoPorIp = maximoPorIp;
    }

    synchronized boolean permitir(String ip) {
        Instant agora = Instant.now();
        if (pedidos.size() > MAXIMO_DE_IPS) {
            pedidos.values().removeIf(lista -> lista.isEmpty() || lista.peekLast().isBefore(agora.minus(JANELA)));
        }
        Deque<Instant> doIp = pedidos.computeIfAbsent(ip == null ? "desconhecido" : ip, chave -> new ArrayDeque<>());
        while (!doIp.isEmpty() && doIp.peekFirst().isBefore(agora.minus(JANELA))) {
            doIp.pollFirst();
        }
        if (doIp.size() >= maximoPorIp) {
            return false;
        }
        doIp.addLast(agora);
        return true;
    }
}
