package br.com.plataforma.identity.equipe;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.UsuarioConsultas;

/**
 * Equipes do tenant, com membros e líderes (Requisito RF57, decisão D16). O token leva as equipes
 * do usuário no claim "equipes", relido a cada renovação: quem entra numa equipe passa a tê-la no
 * token na renovação seguinte.
 */
@Service
public class AdministracaoDeEquipes {

    public record EquipeResumo(UUID id, String nome, int totalMembros, List<String> lideres) {
    }

    public record Membro(UUID id, String nome, boolean lider) {
    }

    public record EquipeDetalhe(UUID id, String nome, List<Membro> membros) {
    }

    public record MembroInformado(UUID usuarioId, boolean lider) {
    }

    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;

    public AdministracaoDeEquipes(JdbcTemplate jdbc, Auditoria auditoria, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.transacao = transacao;
    }

    public Pagina<EquipeResumo> listar(UUID tenant, String busca, int pagina, int tamanho) {
        String filtro = busca == null || busca.isBlank() ? "%" : "%" + busca.strip()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM identity.equipes
                 WHERE tenant_id = ? AND deleted_at IS NULL AND nome ILIKE ? ESCAPE '\\'
                """, Long.class, tenant, filtro);
        List<EquipeResumo> itens = jdbc.query("""
                SELECT e.id, e.nome,
                       count(u.id) AS membros,
                       coalesce(array_agg(u.nome ORDER BY u.nome) FILTER (WHERE ue.lider AND u.id IS NOT NULL), '{}') AS lideres
                  FROM identity.equipes e
                  LEFT JOIN identity.usuario_equipes ue ON ue.equipe_id = e.id
                  LEFT JOIN identity.usuarios u ON u.id = ue.usuario_id AND u.ativo AND u.deleted_at IS NULL
                 WHERE e.tenant_id = ? AND e.deleted_at IS NULL AND e.nome ILIKE ? ESCAPE '\\'
                 GROUP BY e.id, e.nome
                 ORDER BY lower(e.nome), e.id
                 LIMIT ? OFFSET ?
                """, (rs, linha) -> new EquipeResumo(rs.getObject("id", UUID.class), rs.getString("nome"),
                        rs.getInt("membros"), texto(rs.getArray("lideres"))),
                tenant, filtro, tamanho, (long) pagina * tamanho);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    public EquipeDetalhe detalhe(UUID tenant, UUID id) {
        String nome = jdbc.queryForList("SELECT nome FROM identity.equipes WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                String.class, id, tenant).stream().findFirst().orElseThrow(AdministracaoDeEquipes::naoEncontrada);
        return new EquipeDetalhe(id, nome, membros(tenant, id));
    }

    public EquipeDetalhe criar(Ator ator, String nomeInformado, List<MembroInformado> membrosInformados, Origem origem) {
        String nome = nomeInformado.strip();
        List<MembroInformado> membros = semRepetidos(membrosInformados);
        UUID id = UUID.randomUUID();
        transacao.executeWithoutResult(status -> {
            exigirNomeLivre(ator.tenant(), nome, null);
            exigirUsuariosDoTenant(ator.tenant(), membros);
            try {
                jdbc.update("""
                        INSERT INTO identity.equipes (id, tenant_id, nome, created_by, updated_by) VALUES (?, ?, ?, ?, ?)
                        """, id, ator.tenant(), nome, ator.id(), ator.id());
            } catch (DuplicateKeyException e) {
                throw nomeEmUso();
            }
            gravarMembros(id, membros);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "criar", "equipe", id, null, Map.of("nome", nome));
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "alterar", "equipe_membros", id, null,
                    Map.of("membros", descrever(membros(ator.tenant(), id))));
        });
        return detalhe(ator.tenant(), id);
    }

    /** A lista de membros substitui a anterior. */
    public EquipeDetalhe editar(Ator ator, UUID id, String nomeInformado, List<MembroInformado> membrosInformados,
                                Origem origem) {
        String nome = nomeInformado.strip();
        List<MembroInformado> membros = semRepetidos(membrosInformados);
        transacao.executeWithoutResult(status -> {
            EquipeDetalhe antes = detalhe(ator.tenant(), id);
            exigirNomeLivre(ator.tenant(), nome, id);
            exigirUsuariosDoTenant(ator.tenant(), membros);
            try {
                jdbc.update("UPDATE identity.equipes SET nome = ?, updated_at = now(), updated_by = ? WHERE id = ? AND tenant_id = ?",
                        nome, ator.id(), id, ator.tenant());
            } catch (DuplicateKeyException e) {
                throw nomeEmUso();
            }
            jdbc.update("DELETE FROM identity.usuario_equipes WHERE equipe_id = ?", id);
            gravarMembros(id, membros);
            if (!antes.nome().equals(nome)) {
                auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "editar", "equipe", id,
                        Map.of("nome", antes.nome()), Map.of("nome", nome));
            }
            List<String> descricaoAntes = descrever(antes.membros());
            List<String> descricaoDepois = descrever(membros(ator.tenant(), id));
            if (!descricaoAntes.equals(descricaoDepois)) {
                auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "alterar", "equipe_membros", id,
                        Map.of("membros", descricaoAntes), Map.of("membros", descricaoDepois));
            }
        });
        return detalhe(ator.tenant(), id);
    }

    /**
     * Exclusão lógica: a equipe sai do token dos membros na próxima renovação. Registros dos módulos
     * que guardam o equipe_id continuam com o UUID.
     */
    public void excluir(Ator ator, UUID id, Origem origem) {
        transacao.executeWithoutResult(status -> {
            EquipeDetalhe equipe = detalhe(ator.tenant(), id);
            jdbc.update("UPDATE identity.equipes SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE id = ?",
                    ator.id(), id);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "excluir", "equipe", id,
                    Map.of("nome", equipe.nome(), "membros", descrever(equipe.membros())), null);
        });
    }

    private List<Membro> membros(UUID tenant, UUID equipe) {
        return jdbc.query("""
                SELECT u.id, u.nome, ue.lider
                  FROM identity.usuario_equipes ue
                  JOIN identity.usuarios u ON u.id = ue.usuario_id AND u.tenant_id = ? AND u.deleted_at IS NULL
                 WHERE ue.equipe_id = ?
                 ORDER BY ue.lider DESC, lower(u.nome)
                """, (rs, linha) -> new Membro(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getBoolean("lider")),
                tenant, equipe);
    }

    private void gravarMembros(UUID equipe, List<MembroInformado> membros) {
        membros.forEach(membro -> jdbc.update(
                "INSERT INTO identity.usuario_equipes (usuario_id, equipe_id, lider) VALUES (?, ?, ?)",
                membro.usuarioId(), equipe, membro.lider()));
    }

    /** Usuário de outro tenant responde como inexistente. */
    private void exigirUsuariosDoTenant(UUID tenant, List<MembroInformado> membros) {
        if (membros.isEmpty()) {
            return;
        }
        Integer achados = jdbc.queryForObject("""
                SELECT count(*) FROM identity.usuarios
                 WHERE tenant_id = ? AND deleted_at IS NULL AND id = ANY(?::uuid[])
                """, Integer.class, tenant,
                UsuarioConsultas.comoArray(membros.stream().map(MembroInformado::usuarioId).toList()));
        if (achados == null || achados != membros.size()) {
            throw ErroDeNegocio.regra("membros", "USUARIO_INEXISTENTE", "Um dos membros escolhidos não existe mais.");
        }
    }

    private void exigirNomeLivre(UUID tenant, String nome, UUID exceto) {
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.equipes
                                WHERE tenant_id = ? AND deleted_at IS NULL AND lower(nome) = lower(?)
                                  AND id IS DISTINCT FROM ?)
                """, Boolean.class, tenant, nome, exceto);
        if (Boolean.TRUE.equals(usado)) {
            throw nomeEmUso();
        }
    }

    /** O mesmo usuário duas vezes vira um membro só; se alguma das vezes era líder, fica líder. */
    private static List<MembroInformado> semRepetidos(List<MembroInformado> informados) {
        List<MembroInformado> unicos = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (MembroInformado membro : Objects.requireNonNullElse(informados, List.<MembroInformado>of())) {
            if (vistos.add(membro.usuarioId())) {
                boolean lider = informados.stream().anyMatch(m -> m.usuarioId().equals(membro.usuarioId()) && m.lider());
                unicos.add(new MembroInformado(membro.usuarioId(), lider));
            }
        }
        return unicos;
    }

    private static List<String> descrever(List<Membro> membros) {
        return membros.stream().map(membro -> membro.nome() + (membro.lider() ? " (líder)" : "")).sorted().toList();
    }

    private static List<String> texto(Array array) throws java.sql.SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private static ErroDeNegocio nomeEmUso() {
        return ErroDeNegocio.conflito("nome", "NOME_EM_USO", "Já existe uma equipe com este nome.");
    }

    private static NaoEncontradoException naoEncontrada() {
        return new NaoEncontradoException("Equipe não encontrada.");
    }
}
