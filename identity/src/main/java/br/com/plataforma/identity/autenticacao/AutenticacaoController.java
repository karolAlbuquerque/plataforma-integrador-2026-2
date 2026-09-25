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
import br.com.plataforma.identity.autenticacao.Formatos.CodigoDoDesafio;
import br.com.plataforma.identity.autenticacao.Formatos.CredencialDeServico;
import br.com.plataforma.identity.autenticacao.Formatos.Credenciais;
import br.com.plataforma.identity.autenticacao.Formatos.DesafioDeCadastro;
import br.com.plataforma.identity.autenticacao.Formatos.DesafioDeSegundoFator;
import br.com.plataforma.identity.autenticacao.Formatos.DesafioEmitido;
import br.com.plataforma.identity.autenticacao.Formatos.Eu;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoAberta;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoEmitida;
import br.com.plataforma.identity.autenticacao.Formatos.TenantResumo;
import br.com.plataforma.identity.autenticacao.Formatos.TokenDeServico;
import br.com.plataforma.identity.autenticacao.Formatos.UsuarioResumo;
import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.segundofator.SegundoFator.Cadastro;
import br.com.plataforma.identity.seguranca.TokenEmitido;
import br.com.plataforma.identity.seguranca.Usuarios;
import br.com.plataforma.identity.tenant.TenantContexto;
import br.com.plataforma.identity.tenant.Tenants;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;
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
    private final UsuarioRepositorio usuarios;

    public AutenticacaoController(AutenticacaoServico servico, CookieDeRefresh cookies, Tenants tenants,
                                  UsuarioRepositorio usuarios) {
        this.servico = servico;
        this.cookies = cookies;
        this.tenants = tenants;
        this.usuarios = usuarios;
    }

    /**
     * Devolve a sessão ou, se falta o segundo fator, um {@link DesafioDeSegundoFator} — sem cookie e
     * sem access token. A casca distingue pelo campo "desafio".
     */
    @PostMapping("/login")
    public ResponseEntity<? extends Resposta<?>> entrar(@Valid @RequestBody Credenciais corpo,
                                                        HttpServletRequest requisicao) {
        return switch (servico.entrar(corpo.email(), corpo.senha(), Origem.de(requisicao))) {
            case SessaoEmitida sessao -> comSessao(sessao);
            case DesafioEmitido desafio -> ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Resposta.ok(DesafioDeSegundoFator.de(desafio)));
        };
    }

    /** Segunda etapa do login: código do aplicativo ou código de recuperação (um dos dois). */
    @PostMapping("/login/segundo-fator")
    public ResponseEntity<Resposta<SessaoAberta>> concluirComCodigo(@Valid @RequestBody CodigoDoDesafio corpo,
                                                                    HttpServletRequest requisicao) {
        boolean temCodigo = corpo.codigo() != null && !corpo.codigo().isBlank();
        boolean temRecuperacao = corpo.codigoRecuperacao() != null && !corpo.codigoRecuperacao().isBlank();
        if (temCodigo == temRecuperacao) {
            throw ErroDeNegocio.invalido("codigo", "CODIGO_OBRIGATORIO",
                    "Informe o código do aplicativo ou um código de recuperação.");
        }
        return comSessao(servico.concluirComCodigo(corpo.desafio(), temCodigo ? corpo.codigo() : null,
                temRecuperacao ? corpo.codigoRecuperacao() : null, Origem.de(requisicao)));
    }

    /** Primeiro acesso com o segundo fator obrigatório: o segredo e a URI do QR Code. */
    @PostMapping("/login/segundo-fator/cadastro")
    public ResponseEntity<Resposta<Cadastro>> iniciarCadastro(@Valid @RequestBody DesafioDeCadastro corpo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Resposta.ok(servico.iniciarCadastro(corpo.desafio())));
    }

    /** O primeiro código confere: sessão aberta e os dez códigos de recuperação, que só aparecem aqui. */
    @PostMapping("/login/segundo-fator/cadastro/confirmar")
    public ResponseEntity<Resposta<SessaoAberta>> confirmarCadastro(@Valid @RequestBody CodigoDoDesafio corpo,
                                                                    HttpServletRequest requisicao) {
        if (corpo.codigo() == null || corpo.codigo().isBlank()) {
            throw ErroDeNegocio.invalido("codigo", "CODIGO_OBRIGATORIO", "Informe o código do aplicativo.");
        }
        return comSessao(servico.confirmarCadastro(corpo.desafio(), corpo.codigo(), Origem.de(requisicao)));
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
        UUID id = Usuarios.idDe(jwt);
        UsuarioResumo usuario = new UsuarioResumo(id, jwt.getClaimAsString("nome"), jwt.getClaimAsString("email"),
                tenant, usuarios.tema(id));
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
