package br.com.plataforma.identity.equipe;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.tenant.TenantContexto;

/**
 * Membros de uma equipe, para o módulo oferecer a escolha de responsável (Contrato §5.4). Equipe
 * de outro tenant responde 404, como se não existisse.
 */
@RestController
public class EquipeController {

    private final JdbcTemplate jdbc;

    public EquipeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MembroDeEquipe(UUID id, String nome, boolean lider) {
    }

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
}
