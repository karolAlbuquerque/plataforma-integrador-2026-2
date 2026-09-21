package br.com.plataforma.identity.autenticacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Um registro por módulo, com o hash do segredo (Contrato §9.2, Modelo §6.6). */
@Repository
public class CredenciaisDeServico {

    private final JdbcTemplate jdbc;

    public CredenciaisDeServico(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Credencial(UUID id, String clientId, String segredoHash) {
    }

    public Optional<Credencial> buscarAtiva(String clientId) {
        return jdbc.query("""
                SELECT id, client_id, client_secret_hash FROM identity.credenciais_servico
                 WHERE client_id = ? AND ativo
                """, (rs, linha) -> new Credencial(
                        rs.getObject("id", UUID.class), rs.getString("client_id"), rs.getString("client_secret_hash")),
                clientId).stream().findFirst();
    }

    public Optional<Credencial> buscar(String clientId) {
        return jdbc.query("SELECT id, client_id, client_secret_hash FROM identity.credenciais_servico WHERE client_id = ?",
                (rs, linha) -> new Credencial(
                        rs.getObject("id", UUID.class), rs.getString("client_id"), rs.getString("client_secret_hash")),
                clientId).stream().findFirst();
    }

    public List<String> permissoes(UUID credencial) {
        return jdbc.queryForList("""
                SELECT p.codigo
                  FROM identity.credencial_permissoes cp
                  JOIN identity.permissoes p ON p.id = cp.permissao_id
                 WHERE cp.credencial_id = ?
                 ORDER BY p.codigo
                """, String.class, credencial);
    }

    public UUID criar(String clientId, String segredoHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.credenciais_servico (id, client_id, client_secret_hash, modulo_codigo)
                VALUES (?, ?, ?, ?)
                """, id, clientId, segredoHash, clientId);
        return id;
    }

    public void trocarSegredo(UUID id, String segredoHash) {
        jdbc.update("""
                UPDATE identity.credenciais_servico
                   SET client_secret_hash = ?, updated_at = now()
                 WHERE id = ?
                """, segredoHash, id);
    }

    /** As permissões do token de serviço são as que listam este clientId no campo "servicos". */
    public int reconstruirPermissoes(UUID id, String clientId) {
        jdbc.update("DELETE FROM identity.credencial_permissoes WHERE credencial_id = ?", id);
        return jdbc.update("""
                INSERT INTO identity.credencial_permissoes (credencial_id, permissao_id)
                SELECT ?, id FROM identity.permissoes WHERE ? = ANY(servicos)
                """, id, clientId);
    }
}
