package br.com.plataforma.identity.auditoria;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Trilha de auditoria de acesso (Requisitos RF26, RF49 e RF51). Só insere: usr_identity não tem
 * UPDATE nem DELETE em audit_logs. Nunca recebe senha, token nem segredo (RF09, RF12).
 *
 * Mudança de acesso grava o valor anterior e o novo, para que "quem deu esta permissão a este
 * usuário, e quando" se responda só pela auditoria.
 */
@Repository
public class Auditoria {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Auditoria(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * @param tenant   nulo quando ainda não se sabe o tenant (e-mail inexistente, token de serviço)
     * @param usuario  quem agiu; nulo quando não há usuário identificado
     * @param detalhes vai para valor_novo; o que foi tentado ou o que mudou
     */
    public void registrar(UUID tenant, UUID usuario, String ip, String acao, String entidade, UUID entidadeId,
                          Map<String, ?> detalhes) {
        registrar(tenant, usuario, ip, acao, entidade, entidadeId, null, detalhes);
    }

    public void registrar(UUID tenant, UUID usuario, String ip, String acao, String entidade, UUID entidadeId,
                          Map<String, ?> anterior, Map<String, ?> novo) {
        jdbc.update("""
                INSERT INTO identity.audit_logs (id, tenant_id, usuario_id, ip, acao, entidade, entidade_id,
                                                 valor_anterior, valor_novo)
                VALUES (?, ?, ?, ?::inet, ?, ?, ?, ?::jsonb, ?::jsonb)
                """, UUID.randomUUID(), tenant, usuario, ip, acao, entidade, entidadeId, json(anterior), json(novo));
    }

    private String json(Map<String, ?> valor) {
        if (valor == null || valor.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Detalhe de auditoria não serializável.", e);
        }
    }
}
