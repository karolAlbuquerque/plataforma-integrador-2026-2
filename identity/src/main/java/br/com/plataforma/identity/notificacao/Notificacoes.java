package br.com.plataforma.identity.notificacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.api.Pagina;

/**
 * Notificações do usuário (Requisito RF54). Chegam por identity.notificacao.criar e são lidas só
 * pelo próprio destinatário: toda consulta filtra tenant e usuário do token.
 */
@Repository
public class Notificacoes {

    private final JdbcTemplate jdbc;

    public Notificacoes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Notificacao(UUID id, String categoria, String titulo, String texto, String moduloOrigem,
                              String rota, boolean lida, OffsetDateTime criadaEm) {
    }

    public void criar(UUID tenant, UUID usuario, String moduloOrigem, String categoria, String titulo, String texto,
                      String rota) {
        jdbc.update("""
                INSERT INTO identity.notificacoes
                       (id, tenant_id, usuario_id, modulo_origem, categoria, titulo, texto, rota)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant, usuario, moduloOrigem, categoria, titulo, texto, rota);
    }

    /** Da mais recente para a mais antiga; usa idx_notificacoes_usuario. */
    public Pagina<Notificacao> doUsuario(UUID tenant, UUID usuario, boolean soNaoLidas, int pagina, int tamanho) {
        String filtro = soNaoLidas ? " AND lida_em IS NULL" : "";
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM identity.notificacoes WHERE tenant_id = ? AND usuario_id = ?" + filtro,
                Long.class, tenant, usuario);
        List<Notificacao> itens = jdbc.query("""
                SELECT id, categoria, titulo, texto, modulo_origem, rota, lida_em IS NOT NULL AS lida, created_at
                  FROM identity.notificacoes
                 WHERE tenant_id = ? AND usuario_id = ?%s
                 ORDER BY created_at DESC, id
                 LIMIT ? OFFSET ?
                """.formatted(filtro), (rs, linha) -> new Notificacao(
                        rs.getObject("id", UUID.class), rs.getString("categoria"), rs.getString("titulo"),
                        rs.getString("texto"), rs.getString("modulo_origem"), rs.getString("rota"),
                        rs.getBoolean("lida"), rs.getObject("created_at", OffsetDateTime.class)),
                tenant, usuario, tamanho, (long) pagina * tamanho);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    /** Usa idx_notificacoes_nao_lidas: a casca consulta a cada 30 segundos. */
    public long naoLidas(UUID tenant, UUID usuario) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM identity.notificacoes WHERE tenant_id = ? AND usuario_id = ? AND lida_em IS NULL",
                Long.class, tenant, usuario);
        return total == null ? 0 : total;
    }

    /**
     * Idempotente: marcar de novo mantém a data da primeira leitura.
     *
     * @return falso quando a notificação não existe ou é de outro usuário
     */
    public boolean marcarLida(UUID tenant, UUID usuario, UUID id) {
        return jdbc.update("""
                UPDATE identity.notificacoes SET lida_em = coalesce(lida_em, now())
                 WHERE id = ? AND tenant_id = ? AND usuario_id = ?
                """, id, tenant, usuario) > 0;
    }

    /** @return quantas estavam não lidas */
    public int marcarTodasLidas(UUID tenant, UUID usuario) {
        return jdbc.update("""
                UPDATE identity.notificacoes SET lida_em = now()
                 WHERE tenant_id = ? AND usuario_id = ? AND lida_em IS NULL
                """, tenant, usuario);
    }
}
