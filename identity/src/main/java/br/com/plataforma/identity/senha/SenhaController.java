package br.com.plataforma.identity.senha;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.autenticacao.CookieDeRefresh;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.senha.SenhaServico.LinkVerificado;
import br.com.plataforma.identity.seguranca.Ator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Convite, recuperação e troca de senha (contratos/identity.yaml, tag "senha"). Ficam sob
 * /api/identity/auth porque a troca precisa do cookie de refresh, e o cookie só vai para esse
 * caminho. Recuperar, verificar e definir são públicas: quem chega ainda não tem sessão.
 */
@RestController
@RequestMapping("/api/identity/auth/senha")
public class SenhaController {

    public record PedidoDeRecuperacao(
            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
            String email) {
    }

    public record TokenDeSenha(
            @NotBlank(message = "O link está incompleto.")
            @Size(max = 100, message = "O link está incompleto.")
            String token) {
    }

    /** Sem @Size na senha: o tamanho é regra da {@link PoliticaDeSenha}, com a mensagem dela. */
    public record DefinicaoDeSenha(
            @NotBlank(message = "O link está incompleto.")
            @Size(max = 100, message = "O link está incompleto.")
            String token,

            @NotBlank(message = "Informe a nova senha.")
            String novaSenha) {
    }

    public record TrocaDeSenha(
            @NotBlank(message = "Informe a senha atual.")
            String senhaAtual,

            @NotBlank(message = "Informe a nova senha.")
            String novaSenha) {
    }

    private final SenhaServico servico;

    public SenhaController(SenhaServico servico) {
        this.servico = servico;
    }

    @PostMapping("/recuperar")
    public Resposta<Void> pedirRecuperacao(@Valid @RequestBody PedidoDeRecuperacao corpo, HttpServletRequest requisicao) {
        servico.pedirRecuperacao(corpo.email(), Origem.de(requisicao));
        return Resposta.sucesso("Se o e-mail estiver cadastrado, enviaremos um link em instantes.");
    }

    @PostMapping("/verificar")
    public ResponseEntity<Resposta<LinkVerificado>> verificar(@Valid @RequestBody TokenDeSenha corpo) {
        return semCache(Resposta.ok(servico.verificar(corpo.token())));
    }

    @PostMapping("/definir")
    public ResponseEntity<Resposta<Void>> definir(@Valid @RequestBody DefinicaoDeSenha corpo, HttpServletRequest requisicao) {
        servico.definir(corpo.token(), corpo.novaSenha(), Origem.de(requisicao));
        return semCache(Resposta.sucesso("Senha definida. Entre com o seu e-mail e a nova senha."));
    }

    @PostMapping
    public Resposta<Void> trocar(@Valid @RequestBody TrocaDeSenha corpo, @AuthenticationPrincipal Jwt jwt,
                                 @CookieValue(name = CookieDeRefresh.NOME, required = false) String refreshToken,
                                 HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        servico.trocar(ator.id(), corpo.senhaAtual(), corpo.novaSenha(), refreshToken, Origem.de(requisicao));
        return Resposta.sucesso("Senha trocada. As outras sessões foram encerradas.");
    }

    private static <T> ResponseEntity<Resposta<T>> semCache(Resposta<T> corpo) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corpo);
    }
}
