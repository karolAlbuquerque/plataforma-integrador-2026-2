package br.com.plataforma.identity.conta;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.MuitasTentativasException;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.LimiteDeTentativas;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.segundofator.AvisosDeSegundoFator;
import br.com.plataforma.identity.segundofator.SegundoFator;
import br.com.plataforma.identity.segundofator.SegundoFator.Cadastro;
import br.com.plataforma.identity.segundofator.SegundoFator.Situacao;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.UsuarioDeLogin;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O segundo fator em "Minha conta" (Requisito RF10): trocar de aparelho e gerar códigos de
 * recuperação novos. Não há "desativar" (decisão de 25/09). Toda operação pede a senha e, com o
 * segundo fator ativo, um código — quem achou a sessão aberta num computador não troca o aparelho.
 * Senha ou código errado contam no mesmo limite de tentativas do login.
 */
@RestController
@RequestMapping("/api/identity/conta/segundo-fator")
public class SegundoFatorDaContaController {

    public record Confirmacao(
            @NotBlank(message = "Informe a senha.")
            @Size(max = 72, message = "A senha pode ter até 72 caracteres.")
            String senha,

            @Size(max = 10, message = "O código tem seis dígitos.")
            String codigo,

            @Size(max = 20, message = "Código de recuperação inválido.")
            String codigoRecuperacao) {
    }

    public record CodigoNovo(
            @NotBlank(message = "Informe o código do aplicativo.")
            @Size(max = 10, message = "O código tem seis dígitos.")
            String codigo) {
    }

    public record CodigosGerados(List<String> codigosRecuperacao) {
    }

    private final SegundoFator segundoFator;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder senhas;
    private final LimiteDeTentativas tentativas;
    private final Auditoria auditoria;
    private final AvisosDeSegundoFator avisos;
    private final TransactionTemplate transacao;

    public SegundoFatorDaContaController(SegundoFator segundoFator, UsuarioRepositorio usuarios, PasswordEncoder senhas,
                                         LimiteDeTentativas tentativas, Auditoria auditoria,
                                         AvisosDeSegundoFator avisos, TransactionTemplate transacao) {
        this.segundoFator = segundoFator;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.tentativas = tentativas;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.transacao = transacao;
    }

    @GetMapping
    public Resposta<Situacao> situacao(@AuthenticationPrincipal Jwt jwt) {
        return Resposta.ok(segundoFator.situacao(Ator.de(jwt).id()));
    }

    /**
     * Começa a troca de aparelho (ou o cadastro voluntário, com o segundo fator não obrigatório):
     * devolve o segredo novo, que só passa a valer quando o primeiro código dele conferir.
     */
    @PostMapping("/troca")
    public ResponseEntity<Resposta<Cadastro>> iniciarTroca(@Valid @RequestBody Confirmacao corpo,
                                                           @AuthenticationPrincipal Jwt jwt,
                                                           HttpServletRequest requisicao) {
        UsuarioDeLogin usuario = conferirIdentidade(Ator.de(jwt), corpo, Origem.de(requisicao));
        Cadastro cadastro = transacao.execute(status -> {
            SegundoFator.SegredoNovo novo = segundoFator.novoSegredo(usuario.email());
            segundoFator.guardarPendente(usuario.id(), novo.cifrado());
            return novo.cadastro();
        });
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Resposta.ok(cadastro));
    }

    /** O primeiro código do aparelho novo confere: ele passa a valer e o anterior deixa de valer. */
    @PostMapping("/troca/confirmar")
    public ResponseEntity<Resposta<CodigosGerados>> confirmarTroca(@Valid @RequestBody CodigoNovo corpo,
                                                                   @AuthenticationPrincipal Jwt jwt,
                                                                   HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        UsuarioDeLogin usuario = usuario(ator);
        Origem origem = Origem.de(requisicao);
        exigirNaoBloqueado(usuario, origem);
        List<String> codigos = transacao.execute(status -> {
            String pendente = segundoFator.pendente(usuario.id());
            if (pendente == null) {
                throw ErroDeNegocio.regra("codigo", "TROCA_NAO_INICIADA", "Comece a troca de aparelho de novo.");
            }
            OptionalLong passo = segundoFator.conferirProvisorio(pendente, corpo.codigo());
            if (passo.isEmpty()) {
                return null;
            }
            List<String> novos = segundoFator.ativar(usuario.id(), pendente, passo.getAsLong());
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(),
                    usuario.segundoFatorAtivo() ? "trocar_aparelho" : "ativar", "segundo_fator", ator.id(), null);
            return novos;
        });
        if (codigos == null) {
            tentativas.registrar(usuario.email(), origem.ip(), false);
            throw codigoInvalido();
        }
        avisos.ativado(usuario.nome(), usuario.email(), usuario.tenantId());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Resposta.ok(new CodigosGerados(codigos)));
    }

    /** Dez códigos novos; os anteriores, usados ou não, deixam de valer. */
    @PostMapping("/codigos")
    public ResponseEntity<Resposta<CodigosGerados>> gerarCodigos(@Valid @RequestBody Confirmacao corpo,
                                                                 @AuthenticationPrincipal Jwt jwt,
                                                                 HttpServletRequest requisicao) {
        Ator ator = Ator.de(jwt);
        Origem origem = Origem.de(requisicao);
        UsuarioDeLogin usuario = conferirIdentidade(ator, corpo, origem);
        if (!usuario.segundoFatorAtivo()) {
            throw ErroDeNegocio.regra("codigo", "SEGUNDO_FATOR_INATIVO", "Cadastre o aplicativo autenticador primeiro.");
        }
        List<String> codigos = transacao.execute(status -> segundoFator.novosCodigos(usuario.id()));
        auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "gerar_codigos", "segundo_fator", ator.id(), null);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Resposta.ok(new CodigosGerados(codigos)));
    }

    /** Senha e, com o segundo fator ativo, código do aplicativo ou de recuperação. */
    private UsuarioDeLogin conferirIdentidade(Ator ator, Confirmacao corpo, Origem origem) {
        UsuarioDeLogin usuario = usuario(ator);
        exigirNaoBloqueado(usuario, origem);
        if (!senhaConfere(corpo.senha(), usuario.senhaHash())) {
            tentativas.registrar(usuario.email(), origem.ip(), false);
            throw ErroDeNegocio.invalido("senha", "SENHA_INCORRETA", "Senha incorreta.");
        }
        if (!usuario.segundoFatorAtivo()) {
            return usuario;
        }
        boolean temCodigo = corpo.codigo() != null && !corpo.codigo().isBlank();
        boolean temRecuperacao = corpo.codigoRecuperacao() != null && !corpo.codigoRecuperacao().isBlank();
        if (!temCodigo && !temRecuperacao) {
            throw ErroDeNegocio.invalido("codigo", "CODIGO_OBRIGATORIO",
                    "Informe o código do aplicativo ou um código de recuperação.");
        }
        Boolean confere = transacao.execute(status -> temCodigo
                ? segundoFator.conferir(usuario.id(), corpo.codigo())
                : segundoFator.usarCodigoDeRecuperacao(usuario.id(), corpo.codigoRecuperacao()));
        if (!Boolean.TRUE.equals(confere)) {
            tentativas.registrar(usuario.email(), origem.ip(), false);
            throw codigoInvalido();
        }
        if (!temCodigo) {
            int restantes = segundoFator.situacao(usuario.id()).codigosRestantes();
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "codigo_recuperacao_usado", "segundo_fator",
                    ator.id(), Map.of("restantes", restantes));
            avisos.codigoUsado(usuario.nome(), usuario.email(), usuario.tenantId(), restantes);
        }
        return usuario;
    }

    private void exigirNaoBloqueado(UsuarioDeLogin usuario, Origem origem) {
        if (tentativas.bloqueado(usuario.email(), origem.ip())) {
            throw new MuitasTentativasException("Muitas tentativas. Aguarde " + tentativas.janela().toMinutes() + " minutos.");
        }
    }

    private UsuarioDeLogin usuario(Ator ator) {
        return usuarios.buscarPorId(ator.id())
                .filter(u -> u.tenantId().equals(ator.tenant()))
                .orElseThrow(() -> new NaoEncontradoException("Conta não encontrada."));
    }

    private boolean senhaConfere(String informada, String hash) {
        try {
            return hash != null && senhas.matches(informada, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static ErroDeNegocio codigoInvalido() {
        return ErroDeNegocio.invalido("codigo", "CODIGO_INVALIDO", "Código inválido.");
    }
}
