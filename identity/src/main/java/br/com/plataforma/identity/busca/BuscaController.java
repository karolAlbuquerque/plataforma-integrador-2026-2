package br.com.plataforma.identity.busca;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.Resposta;
import br.com.plataforma.identity.tenant.TenantContexto;

/**
 * A parte do identity na busca global (Contrato §8.6, Requisito RF55): usuários do tenant pelo
 * nome ou pelo e-mail. As telas do identity são da própria casca, então a rota é da casca
 * (/admin/usuarios?usuario={id}), e não relativa a um urlFrontend.
 */
@RestController
public class BuscaController {

    static final int MAXIMO_DE_ITENS = 5;

    public record ItemDaBusca(UUID id, String titulo, String subtitulo, String rota) {
    }

    private final JdbcTemplate jdbc;

    public BuscaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/identity/busca")
    @PreAuthorize("hasAuthority('identity.usuario.ver')")
    public Resposta<List<ItemDaBusca>> buscar(@RequestParam(required = false) String q) {
        String termo = q == null ? "" : q.strip();
        if (termo.length() < 2 || termo.length() > 100) {
            throw ErroDeNegocio.invalido("q", "CAMPO_INVALIDO", "A busca precisa ter de 2 a 100 caracteres.");
        }
        // % e _ do usuário valem como texto, não como curinga do LIKE
        String padrao = "%" + termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<ItemDaBusca> itens = jdbc.query("""
                SELECT id, nome, email::text AS email, ativo
                  FROM identity.usuarios
                 WHERE tenant_id = ? AND deleted_at IS NULL AND anonimizado_em IS NULL
                   AND (nome ILIKE ? OR email::text ILIKE ?)
                 ORDER BY ativo DESC, lower(nome)
                 LIMIT ?
                """, (rs, linha) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String subtitulo = rs.getString("email") + (rs.getBoolean("ativo") ? "" : " · desativado");
                    return new ItemDaBusca(id, rs.getString("nome"), subtitulo, "/admin/usuarios?usuario=" + id);
                }, TenantContexto.exigir(), padrao, padrao, MAXIMO_DE_ITENS);
        return Resposta.ok(itens);
    }
}
