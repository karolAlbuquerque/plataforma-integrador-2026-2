package br.com.plataforma.identity.perfil;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.perfil.AdministracaoDePerfis.Dados;
import br.com.plataforma.identity.perfil.AdministracaoDePerfis.PerfilDetalhe;
import br.com.plataforma.identity.perfil.AdministracaoDePerfis.PerfilResumo;
import br.com.plataforma.identity.perfil.AdministracaoDePerfis.PermissoesDoModulo;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.tenant.TenantContexto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Perfis e catálogo de permissões, para as telas da casca (contratos/identity.yaml, tag "perfis"). */
@RestController
public class PerfilController {

    public record CadastroDePerfil(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
            String nome,

            @Size(max = 300, message = "A descrição pode ter até 300 caracteres.")
            String descricao,

            @NotNull(message = "Informe as permissões, mesmo que nenhuma.")
            @Size(max = 2000, message = "Permissões demais.")
            List<@NotBlank(message = "Permissão inválida.") String> permissoes) {

        Dados dados() {
            return new Dados(nome, descricao, permissoes);
        }
    }

    public record Duplicacao(
            @NotBlank(message = "Informe o nome da cópia.")
            @Size(max = 60, message = "O nome pode ter até 60 caracteres.")
            String nome) {
    }

    private final AdministracaoDePerfis perfis;

    public PerfilController(AdministracaoDePerfis perfis) {
        this.perfis = perfis;
    }

    @GetMapping("/api/identity/permissoes")
    @PreAuthorize("hasAuthority('identity.perfil.ver')")
    public Resposta<List<PermissoesDoModulo>> catalogo() {
        return Resposta.ok(perfis.catalogo());
    }

    /** O formulário de usuário também lista perfis — daí a alternativa com usuario.administrar. */
    @GetMapping("/api/identity/perfis")
    @PreAuthorize("hasAnyAuthority('identity.perfil.ver', 'identity.usuario.administrar')")
    public Resposta<Pagina<PerfilResumo>> listar(@RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "100") int tamanho) {
        return Resposta.ok(perfis.listar(TenantContexto.exigir(), Math.max(pagina, 0), Math.clamp(tamanho, 1, 100)));
    }

    @GetMapping("/api/identity/perfis/{id}")
    @PreAuthorize("hasAuthority('identity.perfil.ver')")
    public Resposta<PerfilDetalhe> detalhar(@PathVariable UUID id) {
        return Resposta.ok(perfis.detalhe(TenantContexto.exigir(), id));
    }

    @PostMapping("/api/identity/perfis")
    @PreAuthorize("hasAuthority('identity.perfil.administrar')")
    public ResponseEntity<Resposta<PerfilDetalhe>> criar(@Valid @RequestBody CadastroDePerfil corpo,
                                                         @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        PerfilDetalhe criado = perfis.criar(Ator.de(jwt), corpo.dados(), Origem.de(requisicao));
        return ResponseEntity.created(URI.create("/api/identity/perfis/" + criado.id())).body(Resposta.ok(criado));
    }

    @PutMapping("/api/identity/perfis/{id}")
    @PreAuthorize("hasAuthority('identity.perfil.administrar')")
    public Resposta<PerfilDetalhe> editar(@PathVariable UUID id, @Valid @RequestBody CadastroDePerfil corpo,
                                          @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        return Resposta.ok(perfis.editar(Ator.de(jwt), id, corpo.dados(), Origem.de(requisicao)));
    }

    @PostMapping("/api/identity/perfis/{id}/duplicar")
    @PreAuthorize("hasAuthority('identity.perfil.administrar')")
    public ResponseEntity<Resposta<PerfilDetalhe>> duplicar(@PathVariable UUID id, @Valid @RequestBody Duplicacao corpo,
                                                            @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        PerfilDetalhe copia = perfis.duplicar(Ator.de(jwt), id, corpo.nome(), Origem.de(requisicao));
        return ResponseEntity.created(URI.create("/api/identity/perfis/" + copia.id())).body(Resposta.ok(copia));
    }

    @DeleteMapping("/api/identity/perfis/{id}")
    @PreAuthorize("hasAuthority('identity.perfil.administrar')")
    public Resposta<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        perfis.excluir(Ator.de(jwt), id, Origem.de(requisicao));
        return Resposta.sucesso("Perfil excluído.");
    }
}
