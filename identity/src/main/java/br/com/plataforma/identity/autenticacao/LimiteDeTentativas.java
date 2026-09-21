package br.com.plataforma.identity.autenticacao;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bloqueio do login depois de N falhas numa janela, contadas por e-mail ou por IP (Prompt Mestre
 * §84). Por e-mail protege uma conta de tentativa de senha; por IP, várias contas de um atacante só.
 */
@Repository
public class LimiteDeTentativas {

    private final JdbcTemplate jdbc;
    private final int maximoDeFalhas;
    private final Duration janela;

    public LimiteDeTentativas(JdbcTemplate jdbc,
                              @Value("${identity.login.maximo-falhas}") int maximoDeFalhas,
                              @Value("${identity.login.janela}") Duration janela) {
        this.jdbc = jdbc;
        this.maximoDeFalhas = maximoDeFalhas;
        this.janela = janela;
    }

    public boolean bloqueado(String email, String ip) {
        Boolean bloqueado = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM identity.tentativas_login
                         WHERE email = ?::citext AND NOT sucesso
                           AND created_at > now() - (? * interval '1 second')) >= ?
                    OR (SELECT count(*) FROM identity.tentativas_login
                         WHERE ip = ?::inet AND NOT sucesso
                           AND created_at > now() - (? * interval '1 second')) >= ?
                """, Boolean.class,
                email, janela.toSeconds(), maximoDeFalhas,
                ip, janela.toSeconds(), maximoDeFalhas);
        return Boolean.TRUE.equals(bloqueado);
    }

    public void registrar(String email, String ip, boolean sucesso) {
        jdbc.update("INSERT INTO identity.tentativas_login (id, email, ip, sucesso) VALUES (?, ?::citext, ?::inet, ?)",
                UUID.randomUUID(), email, ip, sucesso);
    }

    public Duration janela() {
        return janela;
    }
}
