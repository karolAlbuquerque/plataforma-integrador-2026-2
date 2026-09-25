package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Consulta e exportação da auditoria — Requisitos RF49, RF50 e RF26. Cada teste usa um tenant só
 * seu, com um perfil montado para ele: a contagem de registros não depende dos outros testes.
 */
class AuditoriaTest extends BaseIntegracao {

    private static final String AUDITORIA = "/api/identity/auditoria";
    private static final OffsetDateTime ONTEM = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

    @Test
    void consultaFiltraEMostraSoOProprioTenant() throws Exception {
        String tenant = criarTenant("Auditada");
        String auditor = auditor(tenant, List.of("identity.auditoria.ver"));
        UUID alvo = criarUsuario(tenant, emailAleatorio("alvo"), "Alvo da mudança");
        UUID autor = idDoUsuario(auditor);
        registrar(tenant, autor, "alterar", "usuario_perfis", alvo,
                "{\"perfis\":[\"VENDEDOR\"]}", "{\"perfis\":[\"VENDEDOR\",\"GESTOR\"]}", ONTEM);
        registrar(tenant, null, "recuperacao_solicitada", "senha", alvo, null, null, ONTEM.minusDays(10));
        // O mesmo alvo em outro tenant e um registro sem tenant: nenhum dos dois pode aparecer
        registrar(EMPRESA_B, null, "alterar", "usuario_perfis", alvo, null, null, ONTEM);
        registrar(null, null, "login_falhou", "sessao", alvo, null, null, ONTEM);
        String token = bearer(tokenDe(auditor));

        // RF26: "quem deu esta permissão a este usuário, e quando"
        mvc.perform(get(AUDITORIA).param("entidade", "usuario_perfis").param("entidadeId", alvo.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].acao").value("alterar"))
                .andExpect(jsonPath("$.data.itens[0].usuario.nome").value("Auditor"))
                .andExpect(jsonPath("$.data.itens[0].valorAnterior.perfis[0]").value("VENDEDOR"))
                .andExpect(jsonPath("$.data.itens[0].valorNovo.perfis[1]").value("GESTOR"));

        mvc.perform(get(AUDITORIA).param("entidadeId", alvo.toString()).header("Authorization", token))
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get(AUDITORIA).param("entidadeId", alvo.toString())
                        .param("de", ONTEM.minusDays(2).toString()).header("Authorization", token))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get(AUDITORIA).param("acao", "recuperacao_solicitada").header("Authorization", token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].usuario").isEmpty());
        // O login do auditor também está lá, filtrado por quem agiu
        mvc.perform(get(AUDITORIA).param("usuarioId", autor.toString()).param("acao", "login").header("Authorization", token))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void semPermissaoResponde403EOAcessoNegadoFicaNaAuditoria() throws Exception {
        String email = emailAleatorio("curioso");
        UUID curioso = criarUsuario(EMPRESA_A, email, "Curioso", "VENDEDOR");

        mvc.perform(get(AUDITORIA).param("acao", "login").header("Authorization", bearer(tokenDe(email))))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("""
                SELECT valor_novo->>'caminho' FROM identity.audit_logs
                 WHERE acao = 'acesso_negado' AND usuario_id = ? AND tenant_id = ?::uuid
                """, String.class, curioso, EMPRESA_A)).containsExactly("/api/identity/auditoria");
    }

    @Test
    void filtroInvalidoResponde400() throws Exception {
        String token = bearer(tokenDe(auditor(criarTenant("Filtros"), List.of("identity.auditoria.ver"))));

        mvc.perform(get(AUDITORIA).param("de", "2026-09-10T00:00:00Z").param("ate", "2026-09-01T00:00:00Z")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("PERIODO_INVALIDO"));
        mvc.perform(get(AUDITORIA).param("acao", "login' OR '1'='1").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("acao"));
    }

    @Test
    void exportaCsvParaOExcelEAExportacaoFicaRegistrada() throws Exception {
        String tenant = criarTenant("Exportada");
        String auditor = auditor(tenant, List.of("identity.auditoria.ver", "identity.auditoria.exportar"));
        UUID alvo = criarUsuario(tenant, emailAleatorio("alvo"), "=HYPERLINK(\"http://golpe\")");
        registrar(tenant, alvo, "editar", "usuario", alvo, null, "{\"nome\":\"Novo; com ponto e vírgula\"}", ONTEM);

        byte[] arquivo = mvc.perform(get(AUDITORIA + "/exportar").param("acao", "editar")
                        .header("Authorization", bearer(tokenDe(auditor))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"auditoria-")))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(arquivo, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿ocorridoEm;usuario;usuarioId;ip;acao;entidade;entidadeId;valorAnterior;valorNovo\r\n");
        List<String> linhas = csv.lines().toList();
        assertThat(linhas).hasSize(2);
        // A célula que viraria fórmula ganha apóstrofo; a que tem ";" vai entre aspas
        assertThat(linhas.get(1)).contains(";\"'=HYPERLINK(\"\"http://golpe\"\")\";")
                .contains(";editar;usuario;" + alvo + ";;")
                .endsWith("\"{\"\"nome\"\":\"\"Novo; com ponto e vírgula\"\"}\"");

        assertThat(jdbc.queryForObject("""
                SELECT valor_novo->>'linhas' FROM identity.audit_logs
                 WHERE acao = 'exportar' AND entidade = 'auditoria' AND tenant_id = ?::uuid
                """, String.class, tenant)).isEqualTo("1");
    }

    @Test
    void exportacaoAcimaDoLimiteResponde422MasAConsultaPaginaNormalmente() throws Exception {
        String tenant = criarTenant("Volumosa");
        jdbc.update("""
                INSERT INTO identity.audit_logs (id, tenant_id, acao, entidade, created_at)
                SELECT gen_random_uuid(), ?::uuid, 'login', 'sessao', now() - make_interval(secs => n)
                  FROM generate_series(1, 10001) AS n
                """, tenant);
        String token = bearer(tokenDe(auditor(tenant, List.of("identity.auditoria.ver", "identity.auditoria.exportar"))));

        mvc.perform(get(AUDITORIA + "/exportar").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].codigo").value("LIMITE_DE_EXPORTACAO"));
        mvc.perform(get(AUDITORIA).param("tamanho", "500").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tamanho").value(100))
                .andExpect(jsonPath("$.data.itens.length()").value(100));
    }

    /** Usuário do tenant com um perfil só com as permissões pedidas; devolve o e-mail. */
    private String auditor(String tenant, List<String> permissoes) {
        String email = emailAleatorio("auditor");
        criarPerfil(tenant, "AUDITOR", permissoes);
        criarUsuario(tenant, email, "Auditor", "AUDITOR");
        return email;
    }

    private void registrar(String tenant, UUID usuario, String acao, String entidade, UUID entidadeId, String anterior,
                           String novo, OffsetDateTime quando) {
        jdbc.update("""
                INSERT INTO identity.audit_logs (id, tenant_id, usuario_id, ip, acao, entidade, entidade_id,
                                                 valor_anterior, valor_novo, created_at)
                VALUES (?, ?::uuid, ?, '203.0.113.7', ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """, UUID.randomUUID(), tenant, usuario, acao, entidade, entidadeId, anterior, novo, quando);
    }
}
