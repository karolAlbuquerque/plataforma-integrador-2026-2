package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.plataforma.identity.manutencao.LimpezaDeRegistros;

/** A limpeza diária apaga só o que já venceu e nunca toca em convites nem na auditoria. */
class LimpezaDeRegistrosTest extends BaseIntegracao {

    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired
    LimpezaDeRegistros limpeza;

    @Test
    void apagaSoOQueVenceu() {
        UUID usuario = criarUsuario(EMPRESA_A, emailAleatorio("limpeza"), "Usuário da limpeza");
        UUID tentativaVelha = tentativa(AGORA.minusDays(31));
        UUID tentativaNova = tentativa(AGORA.minusDays(1));
        UUID mensagemVelha = mensagemProcessada(AGORA.minusDays(91));
        UUID mensagemNova = mensagemProcessada(AGORA.minusDays(89));
        UUID sessaoVelha = sessao(usuario, AGORA.minusDays(31));
        UUID sessaoNova = sessao(usuario, AGORA.minusDays(29));
        UUID recuperacaoVelha = link(usuario, "recuperacao", AGORA.minusDays(31));
        UUID conviteVelho = link(usuario, "convite", AGORA.minusDays(60));
        UUID auditoriaVelha = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.audit_logs (id, acao, entidade, created_at) VALUES (?, 'login', 'sessao', ?)",
                auditoriaVelha, AGORA.minusYears(2));

        limpeza.limpar();

        assertThat(existe("tentativas_login", tentativaVelha)).isFalse();
        assertThat(existe("tentativas_login", tentativaNova)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.eventos_processados WHERE evento_id IN (?, ?)",
                Integer.class, mensagemVelha, mensagemNova)).isEqualTo(1);
        assertThat(existe("refresh_tokens", sessaoVelha)).isFalse();
        assertThat(existe("refresh_tokens", sessaoNova)).isTrue();
        assertThat(existe("recuperacoes_senha", recuperacaoVelha)).isFalse();
        assertThat(existe("recuperacoes_senha", conviteVelho)).as("convite vencido define a situação do usuário").isTrue();
        assertThat(existe("audit_logs", auditoriaVelha)).as("auditoria é só de inserção").isTrue();
    }

    private UUID tentativa(OffsetDateTime quando) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.tentativas_login (id, email, ip, sucesso, created_at) VALUES (?, ?, '10.0.0.1', false, ?)",
                id, emailAleatorio("tentativa"), quando);
        return id;
    }

    private UUID mensagemProcessada(OffsetDateTime quando) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.eventos_processados (evento_id, tipo, processado_em) VALUES (?, 'identity.timeline.registrar', ?)",
                id, quando);
        return id;
    }

    private UUID sessao(UUID usuario, OffsetDateTime expiraEm) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.refresh_tokens (id, usuario_id, token_hash, expira_em, sessao_id, iniciada_em)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, usuario, "hash-" + id, expiraEm, id, expiraEm.minusHours(8));
        return id;
    }

    private UUID link(UUID usuario, String tipo, OffsetDateTime expiraEm) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.recuperacoes_senha (id, usuario_id, tipo, token_hash, expira_em) VALUES (?, ?, ?, ?, ?)",
                id, usuario, tipo, "hash-" + id, expiraEm);
        return id;
    }

    private boolean existe(String tabela, UUID id) {
        String coluna = tabela.equals("eventos_processados") ? "evento_id" : "id";
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identity." + tabela + " WHERE " + coluna + " = ?)", Boolean.class, id));
    }
}
