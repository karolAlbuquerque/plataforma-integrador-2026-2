package br.com.plataforma.identity.perfil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.acesso.RegrasDeAcesso;
import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.api.Pagina;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.UsuarioConsultas;

/**
 * Perfis por empresa e o catálogo global de permissões (Requisitos RF20 a RF22 e RF26).
 *
 * Os dez perfis de sistema não se excluem nem mudam de nome — o catálogo concede permissões novas
 * a eles pelo nome (perfisPadrao) —, mas as permissões deles podem ser ajustadas (RF21). Toda
 * mudança de permissão vai para a auditoria com o antes e o depois.
 */
@Service
public class AdministracaoDePerfis {

    public record PermissaoDoCatalogo(String codigo, String recurso, String acao, String descricao) {
    }

    public record PermissoesDoModulo(String modulo, String nome, List<PermissaoDoCatalogo> permissoes) {
    }

    public record PerfilResumo(UUID id, String nome, String rotulo, String descricao, boolean sistema,
                               int totalUsuarios, int totalPermissoes) {
    }

    public record PerfilDetalhe(UUID id, String nome, String rotulo, String descricao, boolean sistema,
                                int totalUsuarios, List<String> permissoes) {
    }

    public record Dados(String nome, String descricao, List<String> permissoes) {
    }

    private record Atual(UUID id, String nome, String descricao, boolean sistema) {
    }

    private final JdbcTemplate jdbc;
    private final RegrasDeAcesso regras;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;

    public AdministracaoDePerfis(JdbcTemplate jdbc, RegrasDeAcesso regras, Auditoria auditoria,
                                 TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.regras = regras;
        this.auditoria = auditoria;
        this.transacao = transacao;
    }

    /** Catálogo inteiro, agrupado por módulo: a matriz de montagem de perfis cabe numa tela (RF22). */
    public List<PermissoesDoModulo> catalogo() {
        Map<String, PermissoesDoModulo> modulos = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.modulo, coalesce(m.nome, CASE p.modulo WHEN 'identity' THEN 'Plataforma' ELSE p.modulo END) AS nome,
                       p.codigo, p.recurso, p.acao, p.descricao
                  FROM identity.permissoes p
                  LEFT JOIN identity.modulos m ON m.codigo = p.modulo
                 ORDER BY (p.modulo <> 'identity'), coalesce(m.ordem_menu, 0), p.modulo, (p.recurso <> 'modulo'), p.recurso, p.acao
                """, rs -> {
            String nomeDoModulo = rs.getString("nome");
            modulos.computeIfAbsent(rs.getString("modulo"),
                            modulo -> new PermissoesDoModulo(modulo, nomeDoModulo, new ArrayList<>()))
                    .permissoes().add(new PermissaoDoCatalogo(rs.getString("codigo"), rs.getString("recurso"),
                            rs.getString("acao"), rs.getString("descricao")));
        });
        return List.copyOf(modulos.values());
    }

    public Pagina<PerfilResumo> listar(UUID tenant, int pagina, int tamanho) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM identity.perfis WHERE tenant_id = ? AND deleted_at IS NULL",
                Long.class, tenant);
        List<PerfilResumo> itens = jdbc.query("""
                SELECT p.id, p.nome, p.descricao, p.sistema,
                       (SELECT count(*) FROM identity.usuario_perfis up
                          JOIN identity.usuarios u ON u.id = up.usuario_id AND u.deleted_at IS NULL
                         WHERE up.perfil_id = p.id) AS usuarios,
                       (SELECT count(*) FROM identity.perfil_permissoes pp WHERE pp.perfil_id = p.id) AS permissoes
                  FROM identity.perfis p
                 WHERE p.tenant_id = ? AND p.deleted_at IS NULL
                 ORDER BY p.sistema DESC, lower(p.nome), p.id
                 LIMIT ? OFFSET ?
                """, (rs, linha) -> new PerfilResumo(rs.getObject("id", UUID.class), rs.getString("nome"),
                        UsuarioConsultas.rotulo(rs.getString("nome"), rs.getBoolean("sistema")), rs.getString("descricao"),
                        rs.getBoolean("sistema"), rs.getInt("usuarios"), rs.getInt("permissoes")),
                tenant, tamanho, (long) pagina * tamanho);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    public PerfilDetalhe detalhe(UUID tenant, UUID id) {
        Atual perfil = carregar(tenant, id, false);
        Integer usuarios = jdbc.queryForObject("""
                SELECT count(*) FROM identity.usuario_perfis up
                  JOIN identity.usuarios u ON u.id = up.usuario_id AND u.deleted_at IS NULL
                 WHERE up.perfil_id = ?
                """, Integer.class, id);
        return new PerfilDetalhe(perfil.id(), perfil.nome(), UsuarioConsultas.rotulo(perfil.nome(), perfil.sistema()),
                perfil.descricao(), perfil.sistema(), usuarios == null ? 0 : usuarios, List.copyOf(permissoesDo(id)));
    }

    public PerfilDetalhe criar(Ator ator, Dados entrada, Origem origem) {
        Dados dados = normalizar(entrada);
        UUID id = UUID.randomUUID();
        transacao.executeWithoutResult(status -> {
            exigirNomeLivre(ator.tenant(), dados.nome(), null);
            exigirNoCatalogo(dados.permissoes());
            regras.exigirQueTenha(ator, dados.permissoes(), "permissoes", "O perfil teria permissões que você não tem.");
            inserir(ator, id, dados.nome(), dados.descricao());
            gravarPermissoes(id, dados.permissoes());
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "criar", "perfil", id, null,
                    comNulos("nome", dados.nome(), "descricao", dados.descricao()));
            auditarPermissoes(ator, origem, id, dados.nome(), Set.of(), new TreeSet<>(dados.permissoes()));
        });
        return detalhe(ator.tenant(), id);
    }

    /** A lista de permissões substitui a anterior, numa gravação só (RF22). */
    public PerfilDetalhe editar(Ator ator, UUID id, Dados entrada, Origem origem) {
        Dados dados = normalizar(entrada);
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(ator.tenant(), id, true);
            Set<String> antes = permissoesDo(id);
            regras.exigirQueTenha(ator, antes, "id", "Este perfil tem permissões que você não tem.");
            if (atual.sistema() && !atual.nome().equals(dados.nome())) {
                throw ErroDeNegocio.regra("nome", "PERFIL_DE_SISTEMA", "Perfil de sistema não muda de nome.");
            }
            exigirNomeLivre(ator.tenant(), dados.nome(), id);
            exigirNoCatalogo(dados.permissoes());
            Set<String> depois = new TreeSet<>(dados.permissoes());
            Set<String> adicionadas = new TreeSet<>(depois);
            adicionadas.removeAll(antes);
            regras.exigirQueTenha(ator, adicionadas, "permissoes", "O perfil teria permissões que você não tem.");

            try {
                jdbc.update("""
                        UPDATE identity.perfis SET nome = ?, descricao = ?, updated_at = now(), updated_by = ?
                         WHERE id = ? AND tenant_id = ?
                        """, dados.nome(), dados.descricao(), ator.id(), id, ator.tenant());
            } catch (DuplicateKeyException e) {
                throw nomeEmUso();
            }
            Map<String, Object> anterior = new LinkedHashMap<>();
            Map<String, Object> novo = new LinkedHashMap<>();
            if (!atual.nome().equals(dados.nome())) {
                anterior.put("nome", atual.nome());
                novo.put("nome", dados.nome());
            }
            if (!Objects.equals(atual.descricao(), dados.descricao())) {
                anterior.put("descricao", atual.descricao());
                novo.put("descricao", dados.descricao());
            }
            if (!novo.isEmpty()) {
                auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "editar", "perfil", id, anterior, novo);
            }
            if (!antes.equals(depois)) {
                jdbc.update("DELETE FROM identity.perfil_permissoes WHERE perfil_id = ?", id);
                gravarPermissoes(id, dados.permissoes());
                auditarPermissoes(ator, origem, id, dados.nome(), antes, depois);
                regras.exigirAdministradorRestante(ator.tenant(), "permissoes");
            }
        });
        return detalhe(ator.tenant(), id);
    }

    /** A cópia nunca é de sistema e só leva permissões que quem copia já tem. */
    public PerfilDetalhe duplicar(Ator ator, UUID origemId, String nomeInformado, Origem origem) {
        String nome = nomeInformado.strip();
        UUID id = UUID.randomUUID();
        transacao.executeWithoutResult(status -> {
            Atual modelo = carregar(ator.tenant(), origemId, false);
            Set<String> permissoes = permissoesDo(origemId);
            regras.exigirQueTenha(ator, permissoes, "id", "O perfil copiado tem permissões que você não tem.");
            exigirNomeLivre(ator.tenant(), nome, null);
            inserir(ator, id, nome, modelo.descricao());
            gravarPermissoes(id, List.copyOf(permissoes));
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "criar", "perfil", id, null,
                    comNulos("nome", nome, "descricao", modelo.descricao(), "copiaDe", modelo.nome()));
            auditarPermissoes(ator, origem, id, nome, Set.of(), new TreeSet<>(permissoes));
        });
        return detalhe(ator.tenant(), id);
    }

    /** Exclusão lógica. Perfil de sistema e perfil com usuários ficam (RF21). */
    public void excluir(Ator ator, UUID id, Origem origem) {
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(ator.tenant(), id, true);
            if (atual.sistema()) {
                throw ErroDeNegocio.regra("id", "PERFIL_DE_SISTEMA", "Os dez perfis de sistema não podem ser excluídos.");
            }
            regras.exigirQueTenha(ator, permissoesDo(id), "id", "Este perfil tem permissões que você não tem.");
            Integer usuarios = jdbc.queryForObject("""
                    SELECT count(*) FROM identity.usuario_perfis up
                      JOIN identity.usuarios u ON u.id = up.usuario_id AND u.deleted_at IS NULL
                     WHERE up.perfil_id = ?
                    """, Integer.class, id);
            if (usuarios != null && usuarios > 0) {
                throw ErroDeNegocio.regra("id", "PERFIL_EM_USO", usuarios == 1
                        ? "1 usuário ainda tem este perfil. Troque o perfil dele antes de excluir."
                        : usuarios + " usuários ainda têm este perfil. Troque o perfil deles antes de excluir.");
            }
            jdbc.update("UPDATE identity.perfis SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE id = ?",
                    ator.id(), id);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "excluir", "perfil", id,
                    Map.of("nome", atual.nome()), null);
        });
    }

    // ------------------------------------------------------------------ apoio

    private void inserir(Ator ator, UUID id, String nome, String descricao) {
        try {
            jdbc.update("""
                    INSERT INTO identity.perfis (id, tenant_id, nome, descricao, sistema, created_by, updated_by)
                    VALUES (?, ?, ?, ?, false, ?, ?)
                    """, id, ator.tenant(), nome, descricao, ator.id(), ator.id());
        } catch (DuplicateKeyException e) {
            throw nomeEmUso();
        }
    }

    private void gravarPermissoes(UUID perfil, List<String> codigos) {
        if (codigos.isEmpty()) {
            return;
        }
        jdbc.update("""
                INSERT INTO identity.perfil_permissoes (perfil_id, permissao_id)
                SELECT ?, id FROM identity.permissoes WHERE codigo = ANY(string_to_array(?, ','))
                """, perfil, String.join(",", codigos));
    }

    /**
     * O "antes" e o "depois" completos, e a diferença explícita: é o que permite responder "quem
     * deu esta permissão, e quando" só pela auditoria (RF26).
     */
    private void auditarPermissoes(Ator ator, Origem origem, UUID perfil, String nome, Set<String> antes, Set<String> depois) {
        Set<String> adicionadas = new TreeSet<>(depois);
        adicionadas.removeAll(antes);
        Set<String> removidas = new TreeSet<>(antes);
        removidas.removeAll(depois);
        auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "alterar", "perfil_permissoes", perfil,
                Map.of("perfil", nome, "permissoes", new TreeSet<>(antes)),
                Map.of("perfil", nome, "permissoes", new TreeSet<>(depois), "adicionadas", adicionadas, "removidas", removidas));
    }

    private Atual carregar(UUID tenant, UUID id, boolean paraAlterar) {
        return jdbc.query("""
                SELECT id, nome, descricao, sistema FROM identity.perfis
                 WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
                """ + (paraAlterar ? " FOR UPDATE" : ""),
                (rs, linha) -> new Atual(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("descricao"),
                        rs.getBoolean("sistema")), id, tenant)
                .stream().findFirst().orElseThrow(() -> new NaoEncontradoException("Perfil não encontrado."));
    }

    private Set<String> permissoesDo(UUID perfil) {
        return new TreeSet<>(jdbc.queryForList("""
                SELECT pe.codigo FROM identity.perfil_permissoes pp
                  JOIN identity.permissoes pe ON pe.id = pp.permissao_id
                 WHERE pp.perfil_id = ?
                """, String.class, perfil));
    }

    /** Nome único no tenant, sem diferença de maiúsculas. */
    private void exigirNomeLivre(UUID tenant, String nome, UUID exceto) {
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.perfis
                                WHERE tenant_id = ? AND deleted_at IS NULL AND lower(nome) = lower(?)
                                  AND id IS DISTINCT FROM ?)
                """, Boolean.class, tenant, nome, exceto);
        if (Boolean.TRUE.equals(usado)) {
            throw nomeEmUso();
        }
    }

    /** Permissão fora do catálogo nunca vai para um perfil — nem para um token (RF20). */
    private void exigirNoCatalogo(List<String> codigos) {
        if (codigos.isEmpty()) {
            return;
        }
        Set<String> existentes = new HashSet<>(jdbc.queryForList(
                "SELECT codigo FROM identity.permissoes WHERE codigo = ANY(string_to_array(?, ','))",
                String.class, String.join(",", codigos)));
        List<String> desconhecidas = codigos.stream().filter(codigo -> !existentes.contains(codigo)).toList();
        if (!desconhecidas.isEmpty()) {
            throw ErroDeNegocio.regra("permissoes", "PERMISSAO_INEXISTENTE",
                    "Permissão fora do catálogo: " + String.join(", ", desconhecidas.stream().limit(5).toList()) + ".");
        }
    }

    private static Dados normalizar(Dados dados) {
        String descricao = dados.descricao() == null || dados.descricao().isBlank() ? null : dados.descricao().strip();
        List<String> permissoes = new ArrayList<>(new LinkedHashSet<>(
                Objects.requireNonNullElse(dados.permissoes(), List.<String>of()).stream().map(String::strip).toList()));
        return new Dados(dados.nome().strip(), descricao, permissoes);
    }

    private static Map<String, Object> comNulos(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    private static ErroDeNegocio nomeEmUso() {
        return ErroDeNegocio.conflito("nome", "NOME_EM_USO", "Já existe um perfil com este nome.");
    }
}
