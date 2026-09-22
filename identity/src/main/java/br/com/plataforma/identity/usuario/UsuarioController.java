package br.com.plataforma.identity.usuario;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.conta.ContaController;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.tenant.TenantContexto;
import br.com.plataforma.identity.usuario.AdministracaoDeUsuarios.Dados;
import br.com.plataforma.identity.usuario.AdministracaoDeUsuarios.UsuarioSalvo;
import br.com.plataforma.identity.usuario.UsuarioConsultas.Filtro;
import br.com.plataforma.identity.usuario.UsuarioConsultas.UsuarioDaLista;
import br.com.plataforma.identity.usuario.UsuarioConsultas.UsuarioDetalhe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Administração de usuários, para as telas da casca (contratos/identity.yaml, tag "usuarios").
 * Ver exige identity.usuario.ver; mudar, identity.usuario.administrar. As regras ficam em
 * {@link AdministracaoDeUsuarios}.
 */
@RestController
@RequestMapping("/api/identity/usuarios")
public class UsuarioController {

    private static final Set<String> SITUACOES = Set.of(UsuarioConsultas.SITUACAO_ATIVO,
            UsuarioConsultas.SITUACAO_CONVITE_PENDENTE, UsuarioConsultas.SITUACAO_CONVITE_EXPIRADO,
            UsuarioConsultas.SITUACAO_INATIVO);

    public record CadastroDeUsuario(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
            String nome,

            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
            String email,

            @Pattern(regexp = ContaController.TELEFONE,
                    message = "Informe o telefone com DDD, só com números, espaço, parênteses, + e -.")
            String telefone,

            @NotEmpty(message = "Escolha pelo menos um perfil.")
            @Size(max = 50, message = "Escolha até 50 perfis.")
            List<@NotNull(message = "Perfil inválido.") UUID> perfis,

            @Size(max = 50, message = "Escolha até 50 equipes.")
            List<@NotNull(message = "Equipe inválida.") UUID> equipes) {

        Dados dados() {
            return new Dados(nome, email, telefone, perfis, equipes);
        }
    }

    private final UsuarioConsultas consultas;
    private final AdministracaoDeUsuarios administracao;

    public UsuarioController(UsuarioConsultas consultas, AdministracaoDeUsuarios administracao) {
        this.consultas = consultas;
        this.administracao = administracao;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.usuario.ver')")
    public Resposta<Pagina<UsuarioDaLista>> listar(@RequestParam(required = false) String busca,
                                                   @RequestParam(required = false) UUID perfilId,
                                                   @RequestParam(required = false) String situacao,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamanho,
                                                   @RequestParam(defaultValue = "nome,asc") String ordenar) {
        if (busca != null && busca.length() > 100) {
            throw ErroDeNegocio.invalido("busca", "CAMPO_INVALIDO", "A busca pode ter até 100 caracteres.");
        }
        if (situacao != null && !situacao.isBlank() && !SITUACOES.contains(situacao)) {
            throw ErroDeNegocio.invalido("situacao", "CAMPO_INVALIDO",
                    "Situação deve ser ativo, convite_pendente, convite_expirado ou inativo.");
        }
        Filtro filtro = new Filtro(busca, perfilId, situacao == null || situacao.isBlank() ? null : situacao);
        return Resposta.ok(consultas.listar(TenantContexto.exigir(), filtro, Math.max(pagina, 0),
                Math.clamp(tamanho, 1, 100), ordenar));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.usuario.ver')")
    public Resposta<UsuarioDetalhe> detalhar(@PathVariable UUID id) {
        return Resposta.ok(consultas.detalhe(TenantContexto.exigir(), id)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado.")));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('identity.usuario.administrar')")
    public ResponseEntity<Resposta<UsuarioSalvo>> cadastrar(@Valid @RequestBody CadastroDeUsuario corpo,
                                                            @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        UsuarioSalvo salvo = administracao.cadastrar(Ator.de(jwt), corpo.dados(), Origem.de(requisicao));
        return ResponseEntity.created(URI.create("/api/identity/usuarios/" + salvo.usuario().id()))
                .body(Resposta.ok(salvo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.usuario.administrar')")
    public Resposta<UsuarioSalvo> editar(@PathVariable UUID id, @Valid @RequestBody CadastroDeUsuario corpo,
                                         @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        return Resposta.ok(administracao.editar(Ator.de(jwt), id, corpo.dados(), Origem.de(requisicao)));
    }

    @PostMapping("/{id}/desativar")
    @PreAuthorize("hasAuthority('identity.usuario.administrar')")
    public Resposta<UsuarioSalvo> desativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest requisicao) {
        return Resposta.ok(administracao.desativar(Ator.de(jwt), id, Origem.de(requisicao)));
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasAuthority('identity.usuario.administrar')")
    public Resposta<UsuarioSalvo> reativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                           HttpServletRequest requisicao) {
        return Resposta.ok(administracao.reativar(Ator.de(jwt), id, Origem.de(requisicao)));
    }

    @PostMapping("/{id}/convite")
    @PreAuthorize("hasAuthority('identity.usuario.administrar')")
    public Resposta<UsuarioSalvo> reenviarConvite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                                  HttpServletRequest requisicao) {
        return Resposta.ok(administracao.reenviarConvite(Ator.de(jwt), id, Origem.de(requisicao)));
    }
}
