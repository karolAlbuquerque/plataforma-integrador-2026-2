package br.com.plataforma.identity.autenticacao;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.autenticacao.Formatos.CredencialDeServico;
import br.com.plataforma.identity.autenticacao.Formatos.Credenciais;
import br.com.plataforma.identity.autenticacao.Formatos.Eu;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoAberta;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoEmitida;
import br.com.plataforma.identity.autenticacao.Formatos.TenantResumo;
import br.com.plataforma.identity.autenticacao.Formatos.TokenDeServico;
import br.com.plataforma.identity.autenticacao.Formatos.UsuarioResumo;
import br.com.plataforma.identity.seguranca.TokenEmitido;
import br.com.plataforma.identity.seguranca.Usuarios;
import br.com.plataforma.identity.tenant.TenantContexto;
import br.com.plataforma.identity.tenant.Tenants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Rotas de sessão (Contrato §4). O access token vai no corpo e vive só na memória da casca; o
 * refresh token vai no cookie e nunca aparece no corpo.
 */
@RestController
@RequestMapping("/api/identity/auth")
public class AutenticacaoController {

    private final AutenticacaoServico servico;
    private final CookieDeRefresh cookies;
    private final Tenants tenants;

    public AutenticacaoController(AutenticacaoServico servico, CookieDeRefresh cookies, Tenants tenants) {
        this.servico = servico;
        this.cookies = cookies;
        this.tenants = tenants;
    }

    @PostMapping("/login")
    public ResponseEntity<Resposta<SessaoAberta>> entrar(@Valid @RequestBody Credenciais corpo,
                                                         HttpServletRequest requisicao) {
        SessaoEmitida sessao = servico.entrar(corpo.email(), corpo.senha(), Origem.de(requisicao));
        return comSessao(sessao);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Resposta<SessaoAberta>> renovar(
            @CookieValue(name = CookieDeRefresh.NOME, required = false) String refreshToken,
            HttpServletRequest requisicao) {
        return servico.renovar(refreshToken, Origem.de(requisicao))
                .map(this::comSessao)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                        .body(Resposta.falha("Sessão encerrada. Entre novamente.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Resposta<Void>> sair(
            @CookieValue(name = CookieDeRefresh.NOME, required = false) String refreshToken,
            @RequestParam(defaultValue = "false") boolean todas,
            HttpServletRequest requisicao) {
        servico.sair(refreshToken, todas, Origem.de(requisicao));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .body(Resposta.ok(null));
    }

    @GetMapping("/me")
    public Resposta<Eu> quemSouEu(@AuthenticationPrincipal Jwt jwt) {
        if (Usuarios.ehServico(jwt)) {
            throw new AccessDeniedException("Token de serviço não representa um usuário.");
        }
        UUID tenant = TenantContexto.exigir();
        UsuarioResumo usuario = new UsuarioResumo(Usuarios.idDe(jwt), jwt.getClaimAsString("nome"),
                jwt.getClaimAsString("email"), tenant);
        return Resposta.ok(new Eu(
                usuario,
                new TenantResumo(tenant, tenants.nome(tenant).orElse(null)),
                Usuarios.lista(jwt, "roles"),
                Usuarios.lista(jwt, "perms"),
                Usuarios.lista(jwt, "equipes").stream().map(UUID::fromString).toList()));
    }

    @PostMapping("/token-servico")
    public Resposta<TokenDeServico> emitirTokenDeServico(@Valid @RequestBody CredencialDeServico corpo,
                                                         HttpServletRequest requisicao) {
        TokenEmitido token = servico.emitirTokenDeServico(corpo.clientId(), corpo.clientSecret(), Origem.de(requisicao));
        return Resposta.ok(new TokenDeServico(token.valor(), token.validadeEmSegundos()));
    }

    private ResponseEntity<Resposta<SessaoAberta>> comSessao(SessaoEmitida sessao) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.criar(sessao.refreshToken(), sessao.fimDaSessao()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Resposta.ok(SessaoAberta.de(sessao)));
    }
}
