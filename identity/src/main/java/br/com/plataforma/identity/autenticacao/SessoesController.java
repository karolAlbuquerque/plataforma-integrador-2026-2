package br.com.plataforma.identity.autenticacao;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.seguranca.Ator;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Sessões ativas do próprio usuário (Requisito RF06). Sob /api/identity/auth porque só aqui chega o
 * cookie de refresh — é ele que diz qual das sessões é "este dispositivo".
 */
@RestController
@RequestMapping("/api/identity/auth/sessoes")
public class SessoesController {

    public record Sessao(UUID id, String ip, String navegador, Instant iniciadaEm, Instant ultimaRenovacaoEm,
                         Instant expiraEm, boolean atual) {
    }

    private final RefreshTokens refreshTokens;
    private final Auditoria auditoria;

    public SessoesController(RefreshTokens refreshTokens, Auditoria auditoria) {
        this.refreshTokens = refreshTokens;
        this.auditoria = auditoria;
    }

    @GetMapping
    public Resposta<List<Sessao>> listar(@AuthenticationPrincipal Jwt jwt,
                                         @CookieValue(name = CookieDeRefresh.NOME, required = false) String refreshToken) {
        Ator ator = Ator.de(jwt);
        Optional<UUID> atual = refreshTokens.sessaoDoCookie(refreshToken, ator.id());
        return Resposta.ok(refreshTokens.ativas(ator.id()).stream()
                .map(sessao -> new Sessao(sessao.sessaoId(), sessao.ip(), Navegador.descrever(sessao.userAgent()),
                        sessao.iniciadaEm(), sessao.ultimaRenovacaoEm(), sessao.expiraEm(),
                        atual.filter(sessao.sessaoId()::equals).isPresent()))
                .toList());
    }

    /** A próxima renovação daquele dispositivo falha; o access token dele vence em até 15 minutos. */
    @DeleteMapping("/{id}")
    public Resposta<Void> encerrar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        if (refreshTokens.revogarSessao(ator.id(), id) == 0) {
            throw new NaoEncontradoException("Sessão não encontrada.");
        }
        auditoria.registrar(ator.tenant(), ator.id(), Origem.de(requisicao).ip(), "encerrar_sessao", "sessao", id, null);
        return Resposta.sucesso("Sessão encerrada.");
    }

    @DeleteMapping
    public Resposta<Void> encerrarOutras(@AuthenticationPrincipal Jwt jwt,
                                         @CookieValue(name = CookieDeRefresh.NOME, required = false) String refreshToken,
                                         HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        int encerradas = refreshTokens.revogarOutras(ator.id(), refreshTokens.sessaoDoCookie(refreshToken, ator.id()));
        auditoria.registrar(ator.tenant(), ator.id(), Origem.de(requisicao).ip(), "encerrar_outras_sessoes", "sessao",
                null, Map.of("sessoesEncerradas", encerradas));
        return Resposta.sucesso(encerradas == 1 ? "1 sessão encerrada." : encerradas + " sessões encerradas.");
    }
}
