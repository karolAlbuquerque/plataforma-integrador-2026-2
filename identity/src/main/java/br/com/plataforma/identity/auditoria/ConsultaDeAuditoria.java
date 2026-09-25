package br.com.plataforma.identity.auditoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.Pagina;

/**
 * Consulta da trilha de auditoria (Requisito RF50). Só lê os registros do tenant do token: os
 * sem tenant — tentativa com e-mail inexistente, emissão de token de serviço — não aparecem na
 * tela de nenhuma empresa.
 */
@Repository
public class ConsultaDeAuditoria {

    /** Acima disso a exportação é recusada: o CSV é montado em memória. */
    public static final int LIMITE_DE_EXPORTACAO = 10_000;

    private static final Pattern CODIGO = Pattern.compile("^[a-z_]{1,40}$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ConsultaDeAuditoria(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Filtros(UUID usuarioId, String acao, String entidade, UUID entidadeId, OffsetDateTime de,
                          OffsetDateTime ate) {

        public Filtros {
            if (acao != null && !CODIGO.matcher(acao).matches()) {
                throw ErroDeNegocio.invalido("acao", "CAMPO_INVALIDO", "Ação inválida.");
            }
            if (entidade != null && !CODIGO.matcher(entidade).matches()) {
                throw ErroDeNegocio.invalido("entidade", "CAMPO_INVALIDO", "Entidade inválida.");
            }
            if (de != null && ate != null && !ate.isAfter(de)) {
                throw ErroDeNegocio.invalido("ate", "PERIODO_INVALIDO", "O fim do período precisa ser depois do início.");
            }
        }

        /** Só os filtros usados, para registrar a exportação na própria auditoria. */
        public Map<String, Object> usados() {
            Map<String, Object> usados = new LinkedHashMap<>();
            usados.put("usuarioId", usuarioId);
            usados.put("acao", acao);
            usados.put("entidade", entidade);
            usados.put("entidadeId", entidadeId);
            usados.put("de", de == null ? null : de.toString());
            usados.put("ate", ate == null ? null : ate.toString());
            usados.values().removeIf(Objects::isNull);
            return usados;
        }
    }

    public record UsuarioDoRegistro(UUID id, String nome) {
    }

    public record RegistroDeAuditoria(UUID id, OffsetDateTime ocorridoEm, UsuarioDoRegistro usuario, String ip,
                                      String acao, String entidade, UUID entidadeId, JsonNode valorAnterior,
                                      JsonNode valorNovo) {
    }

    /** Do mais recente para o mais antigo; usa idx_audit_tenant_data ou idx_audit_tenant_usuario. */
    public Pagina<RegistroDeAuditoria> consultar(UUID tenant, Filtros filtros, int pagina, int tamanho) {
        Condicoes condicoes = condicoes(tenant, filtros);
        long total = contar(condicoes);
        List<Object> parametros = new ArrayList<>(condicoes.parametros());
        parametros.add(tamanho);
        parametros.add((long) pagina * tamanho);
        List<RegistroDeAuditoria> itens = jdbc.query(SELECT + condicoes.sql() + " ORDER BY a.created_at DESC, a.id LIMIT ? OFFSET ?",
                this::registro, parametros.toArray());
        return new Pagina<>(itens, pagina, tamanho, total);
    }

    /** Todos os registros dos filtros, até {@link #LIMITE_DE_EXPORTACAO}; acima disso, 422. */
    public List<RegistroDeAuditoria> paraExportar(UUID tenant, Filtros filtros) {
        Condicoes condicoes = condicoes(tenant, filtros);
        if (contar(condicoes) > LIMITE_DE_EXPORTACAO) {
            throw ErroDeNegocio.regra("de", "LIMITE_DE_EXPORTACAO",
                    "A exportação passa de 10.000 registros. Escolha um período menor.");
        }
        return jdbc.query(SELECT + condicoes.sql() + " ORDER BY a.created_at DESC, a.id", this::registro,
                condicoes.parametros().toArray());
    }

    private static final String SELECT = """
            SELECT a.id, a.created_at, a.usuario_id, coalesce(u.nome, 'Usuário removido') AS nome, host(a.ip) AS ip,
                   a.acao, a.entidade, a.entidade_id, a.valor_anterior::text AS anterior, a.valor_novo::text AS novo
              FROM identity.audit_logs a
              LEFT JOIN identity.usuarios u ON u.id = a.usuario_id AND u.tenant_id = a.tenant_id
            """;

    private record Condicoes(String sql, List<Object> parametros) {
    }

    private static Condicoes condicoes(UUID tenant, Filtros filtros) {
        StringBuilder sql = new StringBuilder(" WHERE a.tenant_id = ?");
        List<Object> parametros = new ArrayList<>(List.of(tenant));
        if (filtros.usuarioId() != null) {
            sql.append(" AND a.usuario_id = ?");
            parametros.add(filtros.usuarioId());
        }
        if (filtros.acao() != null) {
            sql.append(" AND a.acao = ?");
            parametros.add(filtros.acao());
        }
        if (filtros.entidade() != null) {
            sql.append(" AND a.entidade = ?");
            parametros.add(filtros.entidade());
        }
        if (filtros.entidadeId() != null) {
            sql.append(" AND a.entidade_id = ?");
            parametros.add(filtros.entidadeId());
        }
        if (filtros.de() != null) {
            sql.append(" AND a.created_at >= ?");
            parametros.add(filtros.de());
        }
        if (filtros.ate() != null) {
            sql.append(" AND a.created_at < ?");
            parametros.add(filtros.ate());
        }
        return new Condicoes(sql.toString(), parametros);
    }

    private long contar(Condicoes condicoes) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM identity.audit_logs a" + condicoes.sql(), Long.class,
                condicoes.parametros().toArray());
        return total == null ? 0 : total;
    }

    private RegistroDeAuditoria registro(ResultSet rs, int linha) throws SQLException {
        UUID usuario = rs.getObject("usuario_id", UUID.class);
        return new RegistroDeAuditoria(
                rs.getObject("id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                usuario == null ? null : new UsuarioDoRegistro(usuario, rs.getString("nome")),
                rs.getString("ip"),
                rs.getString("acao"),
                rs.getString("entidade"),
                rs.getObject("entidade_id", UUID.class),
                json(rs.getString("anterior")),
                json(rs.getString("novo")));
    }

    private JsonNode json(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return mapper.readTree(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit_logs com JSON ilegível.", e);
        }
    }
}
