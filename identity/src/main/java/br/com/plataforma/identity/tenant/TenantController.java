package br.com.plataforma.identity.tenant;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.CandidatoDoTenant;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.DadosDeCriacao;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.DadosDeEdicao;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.NovoUsuarioDoTenant;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.TenantSalvo;
import br.com.plataforma.identity.tenant.AdministracaoDeTenants.UsuariosDoTenant;
import br.com.plataforma.identity.tenant.Tenants.TenantDaLista;
import br.com.plataforma.identity.tenant.Tenants.TenantDetalhe;
import br.com.plataforma.identity.tenant.Tenants.TenantResolvido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Empresas contratantes (Requisitos RF27 e RF31):
 * - {@code GET /api/identity/tenants/resolver}: superfície pública para os módulos descobrirem o
 *   tenant pelo subdomínio com token de serviço.
 * - Demais rotas: administração de tenants na casca (listagem, cadastro com semeadura dos dez
 *   perfis, edição dos dados cadastrais, gestão de usuários da empresa e ativação/desativação).
 */
@RestController
@RequestMapping("/api/identity/tenants")
public class TenantController {

    private static final Pattern SUBDOMINIO = Pattern.compile("^[a-z0-9-]{1,63}$");

    public record CadastroDeTenant(
            @NotBlank(message = "Informe a razão social.")
            @Size(max = 160, message = "A razão social pode ter até 160 caracteres.")
            String razaoSocial,

            @Size(max = 120, message = "O nome fantasia pode ter até 120 caracteres.")
            String nomeFantasia,

            @Size(max = 20, message = "O CNPJ deve ter 14 dígitos.")
            String cnpj,

            @Size(max = 63, message = "O subdomínio pode ter até 63 caracteres.")
            String subdominio,

            @Size(max = 120, message = "O nome do administrador pode ter até 120 caracteres.")
            String adminNome,

            @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
            String adminEmail) {
    }

    public record EdicaoDeTenant(
            @NotBlank(message = "Informe a razão social.")
            @Size(max = 160, message = "A razão social pode ter até 160 caracteres.")
            String razaoSocial,

            @Size(max = 120, message = "O nome fantasia pode ter até 120 caracteres.")
            String nomeFantasia,

            @Size(max = 20, message = "O CNPJ deve ter 14 dígitos.")
            String cnpj,

            @Size(max = 63, message = "O subdomínio pode ter até 63 caracteres.")
            String subdominio) {
    }

    public record VinculoDeUsuarioNoTenant(
            @NotNull(message = "Escolha o usuário.")
            UUID usuarioId,

            @NotNull(message = "Escolha o perfil.")
            UUID perfilId) {
    }

    public record CadastroDeUsuarioNoTenant(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 120, message = "O nome pode ter até 120 caracteres.")
            String nome,

            @NotBlank(message = "Informe o e-mail.")
            @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
            String email,

            @Size(max = 20, message = "O telefone pode ter até 20 caracteres.")
            String telefone,

            @NotNull(message = "Escolha o perfil.")
            UUID perfilId) {
    }

    private final Tenants tenants;
    private final AdministracaoDeTenants administracao;

    public TenantController(Tenants tenants, AdministracaoDeTenants administracao) {
        this.tenants = tenants;
        this.administracao = administracao;
    }

    @GetMapping("/resolver")
    @PreAuthorize("hasAuthority('identity.tenant.ver')")
    public Resposta<TenantResolvido> resolver(@RequestParam String subdominio) {
        String normalizado = subdominio.strip().toLowerCase(Locale.ROOT);
        if (!SUBDOMINIO.matcher(normalizado).matches()) {
            throw ErroDeNegocio.invalido("subdominio", "CAMPO_INVALIDO", "Subdomínio inválido.");
        }
        return tenants.ativoPorSubdominio(normalizado)
                .map(Resposta::ok)
                .orElseThrow(() -> new NaoEncontradoException("Nenhuma empresa ativa com esse subdomínio."));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<Pagina<TenantDaLista>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String situacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @AuthenticationPrincipal Jwt jwt) {
        if (busca != null && busca.length() > 100) {
            throw ErroDeNegocio.invalido("busca", "CAMPO_INVALIDO", "A busca pode ter até 100 caracteres.");
        }
        if (situacao != null && !situacao.isBlank() && !situacao.equals("ativo") && !situacao.equals("inativo")) {
            throw ErroDeNegocio.invalido("situacao", "CAMPO_INVALIDO", "A situação deve ser ativo ou inativo.");
        }
        Ator ator = Ator.de(jwt);
        administracao.garantirVinculoNoTenantAtivo(ator);
        int paginaLimpa = Math.max(0, pagina);
        int tamanhoLimpo = Math.clamp(tamanho, 1, 100);
        return Resposta.ok(tenants.listar(ator.tenant(), busca, situacao, paginaLimpa, tamanhoLimpo));
    }

    @GetMapping("/atual")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<TenantDetalhe> atual(@AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        administracao.garantirVinculoNoTenantAtivo(ator);
        return tenants.detalhe(ator.tenant(), ator.tenant())
                .map(Resposta::ok)
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<TenantDetalhe> detalhar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Ator ator = Ator.de(jwt);
        administracao.garantirVinculoNoTenantAtivo(ator);
        return tenants.detalhe(id, ator.tenant())
                .map(Resposta::ok)
                .orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public ResponseEntity<Resposta<TenantSalvo>> cadastrar(@Valid @RequestBody CadastroDeTenant corpo,
                                                           @AuthenticationPrincipal Jwt jwt,
                                                           HttpServletRequest requisicao) {
        TenantSalvo salvo = administracao.cadastrar(Ator.de(jwt),
                new DadosDeCriacao(corpo.razaoSocial(), corpo.nomeFantasia(), corpo.cnpj(), corpo.subdominio(),
                        corpo.adminNome(), corpo.adminEmail()),
                Origem.de(requisicao));
        return ResponseEntity.created(URI.create("/api/identity/tenants/" + salvo.tenant().id()))
                .body(Resposta.ok(salvo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<TenantDetalhe> editar(@PathVariable UUID id, @Valid @RequestBody EdicaoDeTenant corpo,
                                          @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        TenantDetalhe atualizado = administracao.editar(Ator.de(jwt), id,
                new DadosDeEdicao(corpo.razaoSocial(), corpo.nomeFantasia(), corpo.cnpj(), corpo.subdominio()),
                Origem.de(requisicao));
        return Resposta.ok(atualizado);
    }

    @PostMapping("/{id}/desativar")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<TenantDetalhe> desativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                             HttpServletRequest requisicao) {
        return Resposta.ok(administracao.desativar(Ator.de(jwt), id, Origem.de(requisicao)));
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<TenantDetalhe> reativar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest requisicao) {
        return Resposta.ok(administracao.reativar(Ator.de(jwt), id, Origem.de(requisicao)));
    }

    // ------------------------------------------------------------------ usuários do tenant

    @GetMapping("/{id}/usuarios")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<UsuariosDoTenant> listarUsuarios(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        administracao.garantirVinculoNoTenantAtivo(Ator.de(jwt));
        return Resposta.ok(administracao.listarUsuarios(id));
    }

    @GetMapping("/{id}/usuarios/candidatos")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<List<CandidatoDoTenant>> buscarCandidatos(@PathVariable UUID id,
                                                              @RequestParam(required = false) String busca) {
        return Resposta.ok(administracao.buscarCandidatos(id, busca));
    }

    @PostMapping("/{id}/usuarios/vincular")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<UsuariosDoTenant> vincularUsuario(@PathVariable UUID id,
                                                      @Valid @RequestBody VinculoDeUsuarioNoTenant corpo,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      HttpServletRequest requisicao) {
        return Resposta.ok(administracao.vincularUsuarioExistente(
                Ator.de(jwt), id, corpo.usuarioId(), corpo.perfilId(), Origem.de(requisicao)));
    }

    @PostMapping("/{id}/usuarios")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<UsuariosDoTenant> cadastrarUsuarioNoTenant(@PathVariable UUID id,
                                                               @Valid @RequestBody CadastroDeUsuarioNoTenant corpo,
                                                               @AuthenticationPrincipal Jwt jwt,
                                                               HttpServletRequest requisicao) {
        return Resposta.ok(administracao.cadastrarNovoUsuarioNoTenant(
                Ator.de(jwt), id, new NovoUsuarioDoTenant(corpo.nome(), corpo.email(), corpo.telefone(), corpo.perfilId()),
                Origem.de(requisicao)));
    }

    @DeleteMapping("/{id}/usuarios/{usuarioId}")
    @PreAuthorize("hasAuthority('identity.tenant.administrar')")
    public Resposta<UsuariosDoTenant> removerUsuario(@PathVariable UUID id, @PathVariable UUID usuarioId,
                                                     @AuthenticationPrincipal Jwt jwt,
                                                     HttpServletRequest requisicao) {
        return Resposta.ok(administracao.removerUsuarioDoTenant(Ator.de(jwt), id, usuarioId, Origem.de(requisicao)));
    }
}
