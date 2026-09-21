package br.com.plataforma.identity.timeline;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.api.Pagina;

/** Timeline única da empresa, alimentada pelos oito módulos por mensagem (Modelo §6.4). */
@Repository
public class Timeline {

    private final JdbcTemplate jdbc;

    public Timeline(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EventoDaTimeline(UUID id, String tipo, String moduloOrigem, String texto, String rota,
                                   UUID usuarioId, OffsetDateTime ocorridoEm) {
    }

    public void registrar(UUID tenant, UUID empresa, String moduloOrigem, String tipo, String texto, String rota,
                          UUID usuario, OffsetDateTime ocorridoEm) {
        jdbc.update("""
                INSERT INTO identity.eventos_timeline
                       (id, tenant_id, empresa_id, modulo_origem, tipo, texto, rota, usuario_id, ocorrido_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant, empresa, moduloOrigem, tipo, texto, rota, usuario, ocorridoEm);
    }

    /** Do fato mais recente para o mais antigo; usa idx_timeline_empresa. */
    public Pagina<EventoDaTimeline> daEmpresa(UUID tenant, UUID empresa, int pagina, int tamanho) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM identity.eventos_timeline WHERE tenant_id = ? AND empresa_id = ?",
                Long.class, tenant, empresa);
        List<EventoDaTimeline> itens = jdbc.query("""
                SELECT id, tipo, modulo_origem, texto, rota, usuario_id, ocorrido_em
                  FROM identity.eventos_timeline
                 WHERE tenant_id = ? AND empresa_id = ?
                 ORDER BY ocorrido_em DESC, id
                 LIMIT ? OFFSET ?
                """, (rs, linha) -> new EventoDaTimeline(
                        rs.getObject("id", UUID.class), rs.getString("tipo"), rs.getString("modulo_origem"),
                        rs.getString("texto"), rs.getString("rota"), rs.getObject("usuario_id", UUID.class),
                        rs.getObject("ocorrido_em", OffsetDateTime.class)),
                tenant, empresa, tamanho, (long) pagina * tamanho);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }
}
