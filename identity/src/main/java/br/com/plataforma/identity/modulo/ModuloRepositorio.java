package br.com.plataforma.identity.modulo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Registro de módulos (Contrato §11). Característica da instalação, sem tenant_id (Modelo §6.3). */
@Repository
public class ModuloRepositorio {

    private final JdbcTemplate jdbc;

    public ModuloRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ItemDeMenu(String rota, String nome, String permissao) {
    }

    public record Modulo(String codigo, String nome, String grupo, String icone, String urlFrontend,
                         String prefixoApi, String permissaoMenu, int ordemMenu, String healthcheck,
                         List<ItemDeMenu> itensSubmenu, boolean busca) {
    }

    /** Módulos ativos, na ordem do menu, com os itens de submenu na ordem do registro. */
    public List<Modulo> ativos() {
        Map<UUID, List<ItemDeMenu>> itens = new LinkedHashMap<>();
        jdbc.query("SELECT modulo_id, rota, nome, permissao FROM identity.modulo_menu_itens ORDER BY modulo_id, ordem",
                rs -> {
                    itens.computeIfAbsent(rs.getObject("modulo_id", UUID.class), id -> new ArrayList<>())
                            .add(new ItemDeMenu(rs.getString("rota"), rs.getString("nome"), rs.getString("permissao")));
                });
        return jdbc.query("""
                SELECT id, codigo, nome, grupo, icone, url_frontend, prefixo_api, permissao_menu, ordem_menu, healthcheck,
                       busca
                  FROM identity.modulos
                 WHERE ativo
                 ORDER BY ordem_menu, nome
                """, (rs, linha) -> new Modulo(
                rs.getString("codigo"), rs.getString("nome"), rs.getString("grupo"), rs.getString("icone"),
                rs.getString("url_frontend"), rs.getString("prefixo_api"), rs.getString("permissao_menu"),
                rs.getInt("ordem_menu"), rs.getString("healthcheck"),
                List.copyOf(itens.getOrDefault(rs.getObject("id", UUID.class), List.of())), rs.getBoolean("busca")));
    }

    /** Grava o registro vindo de modulos/{codigo}.json. Não mexe em "ativo": quem desliga é o administrador. */
    @Transactional
    public void salvar(Modulo modulo) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO identity.modulos (id, codigo, nome, grupo, icone, url_frontend, prefixo_api,
                                              permissao_menu, ordem_menu, healthcheck, busca)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (codigo) DO UPDATE
                   SET nome = EXCLUDED.nome, grupo = EXCLUDED.grupo, icone = EXCLUDED.icone,
                       url_frontend = EXCLUDED.url_frontend, prefixo_api = EXCLUDED.prefixo_api,
                       permissao_menu = EXCLUDED.permissao_menu, ordem_menu = EXCLUDED.ordem_menu,
                       healthcheck = EXCLUDED.healthcheck, busca = EXCLUDED.busca, updated_at = now()
                RETURNING id
                """, UUID.class, UUID.randomUUID(), modulo.codigo(), modulo.nome(), modulo.grupo(), modulo.icone(),
                modulo.urlFrontend(), modulo.prefixoApi(), modulo.permissaoMenu(), modulo.ordemMenu(),
                modulo.healthcheck(), modulo.busca());

        jdbc.update("DELETE FROM identity.modulo_menu_itens WHERE modulo_id = ?", id);
        List<ItemDeMenu> itens = modulo.itensSubmenu();
        for (int ordem = 0; ordem < itens.size(); ordem++) {
            ItemDeMenu item = itens.get(ordem);
            jdbc.update("""
                    INSERT INTO identity.modulo_menu_itens (id, modulo_id, rota, nome, permissao, ordem)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, item.rota(), item.nome(), item.permissao(), ordem);
        }
    }
}
