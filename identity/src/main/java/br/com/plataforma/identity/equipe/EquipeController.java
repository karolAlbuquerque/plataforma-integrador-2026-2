package br.com.plataforma.identity.equipe;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.equipe.AdministracaoDeEquipes.EquipeDetalhe;
import br.com.plataforma.identity.equipe.AdministracaoDeEquipes.EquipeResumo;
import br.com.plataforma.identity.equipe.AdministracaoDeEquipes.MembroInformado;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.tenant.TenantContexto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Equipes (Contrato §5.4, Requisito RF57). Ler exige identity.equipe.ver_resumo — os módulos usam
 * para escolher responsável e filtrar por equipe; mudar exige identity.equipe.administrar. Equipe
 * de outro tenant responde 404, como se não existisse.
 */
@RestController
public class EquipeController {

    public record MembroDeEquipe(UUID id, String nome, boolean lider) {
    }

    public record CadastroDeEquipe(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 80, message = "O nome pode ter até 80 caracteres.")
            String nome,

            @NotNull(message = "Informe os membros, mesmo que nenhum.")
            @Size(max = 500, message = "Membros demais.")
            List<@Valid @NotNull(message = "Membro inválido.") Membro> membros) {
    }

    public record Membro(@NotNull(message = "Informe o usuário.") UUID usuarioId, boolean lider) {
    }

    private final JdbcTemplate jdbc;
    private final AdministracaoDeEquipes equipes;

    public EquipeController(JdbcTemplate jdbc, AdministracaoDeEquipes equipes) {
        this.jdbc = jdbc;
        this.equipes = equipes;
    }

    @GetMapping("/api/identity/equipes")
    @PreAuthorize("hasAuthority('identity.equipe.ver_resumo')")
    public Resposta<Pagina<EquipeResumo>> listar(@RequestParam(required = false) String busca,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "20") int tamanho) {
        if (busca != null && busca.length() > 100) {
            throw ErroDeNegocio.invalido("busca", "CAMPO_INVALIDO", "A busca pode ter até 100 caracteres.");
        }
        return Resposta.ok(equipes.listar(TenantContexto.exigir(), busca, Math.max(pagina, 0), Math.clamp(tamanho, 1, 100)));
    }

    @GetMapping("/api/identity/equipes/{id}")
    @PreAuthorize("hasAuthority('identity.equipe.ver_resumo')")
    public Resposta<EquipeDetalhe> detalhar(@PathVariable UUID id) {
        return Resposta.ok(equipes.detalhe(TenantContexto.exigir(), id));
    }

    @PostMapping("/api/identity/equipes")
    @PreAuthorize("hasAuthority('identity.equipe.administrar')")
    public ResponseEntity<Resposta<EquipeDetalhe>> criar(@Valid @RequestBody CadastroDeEquipe corpo,
                                                         @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        EquipeDetalhe criada = equipes.criar(Ator.de(jwt), corpo.nome(), membros(corpo), Origem.de(requisicao));
        return ResponseEntity.created(URI.create("/api/identity/equipes/" + criada.id())).body(Resposta.ok(criada));
    }

    @PutMapping("/api/identity/equipes/{id}")
    @PreAuthorize("hasAuthority('identity.equipe.administrar')")
    public Resposta<EquipeDetalhe> editar(@PathVariable UUID id, @Valid @RequestBody CadastroDeEquipe corpo,
                                          @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        return Resposta.ok(equipes.editar(Ator.de(jwt), id, corpo.nome(), membros(corpo), Origem.de(requisicao)));
    }

    @DeleteMapping("/api/identity/equipes/{id}")
    @PreAuthorize("hasAuthority('identity.equipe.administrar')")
    public Resposta<Void> excluir(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest requisicao) {
        equipes.excluir(Ator.de(jwt), id, Origem.de(requisicao));
        return Resposta.sucesso("Equipe excluída.");
    }

    /** Membros de uma equipe, para o módulo oferecer a escolha de responsável (Contrato §5.4). */
    @GetMapping("/api/identity/equipes/{id}/membros")
    @PreAuthorize("hasAuthority('identity.equipe.ver_resumo')")
    public Resposta<List<MembroDeEquipe>> membros(@PathVariable UUID id) {
        UUID tenant = TenantContexto.exigir();
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.equipes WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL)
                """, Boolean.class, id, tenant);
        if (!Boolean.TRUE.equals(existe)) {
            throw new NaoEncontradoException("Equipe não encontrada.");
        }
        List<MembroDeEquipe> membros = jdbc.query("""
                SELECT u.id, u.nome, ue.lider
                  FROM identity.usuario_equipes ue
                  JOIN identity.usuarios u ON u.id = ue.usuario_id
                 WHERE ue.equipe_id = ? AND u.tenant_id = ? AND u.deleted_at IS NULL AND u.ativo
                 ORDER BY ue.lider DESC, u.nome
                """, (rs, linha) -> new MembroDeEquipe(
                        rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBoolean("lider")),
                id, tenant);
        return Resposta.ok(membros);
    }

    private static List<MembroInformado> membros(CadastroDeEquipe corpo) {
        return corpo.membros().stream().map(membro -> new MembroInformado(membro.usuarioId(), membro.lider())).toList();
    }
}
