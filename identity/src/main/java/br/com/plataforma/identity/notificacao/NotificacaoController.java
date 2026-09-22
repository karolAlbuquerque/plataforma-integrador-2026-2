package br.com.plataforma.identity.notificacao;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.notificacao.Notificacoes.Notificacao;
import br.com.plataforma.identity.seguranca.Ator;

/**
 * O sino da casca (Requisito RF54). Sem permissão própria: cada usuário vê as suas. Token de
 * serviço não tem notificações — {@link Ator#de} responde 403.
 */
@RestController
@RequestMapping("/api/identity/notificacoes")
public class NotificacaoController {

    private final Notificacoes notificacoes;

    public NotificacaoController(Notificacoes notificacoes) {
        this.notificacoes = notificacoes;
    }

    @GetMapping
    public Resposta<Pagina<Notificacao>> listar(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "false") boolean naoLidas,
                                                @RequestParam(defaultValue = "0") int pagina,
                                                @RequestParam(defaultValue = "20") int tamanho) {
        Ator ator = Ator.de(jwt);
        return Resposta.ok(notificacoes.doUsuario(ator.tenant(), ator.id(), naoLidas,
                Math.max(pagina, 0), Math.clamp(tamanho, 1, 100)));
    }

    @GetMapping("/contagem")
    public Resposta<Map<String, Long>> contagem(@AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        return Resposta.ok(Map.of("naoLidas", notificacoes.naoLidas(ator.tenant(), ator.id())));
    }

    @PostMapping("/{id}/lida")
    public Resposta<Void> marcarLida(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Ator ator = Ator.de(jwt);
        if (!notificacoes.marcarLida(ator.tenant(), ator.id(), id)) {
            throw new NaoEncontradoException("Notificação não encontrada.");
        }
        return Resposta.ok(null);
    }

    @PostMapping("/lidas")
    public Resposta<Map<String, Integer>> marcarTodasLidas(@AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        return Resposta.ok(Map.of("marcadas", notificacoes.marcarTodasLidas(ator.tenant(), ator.id())));
    }
}
