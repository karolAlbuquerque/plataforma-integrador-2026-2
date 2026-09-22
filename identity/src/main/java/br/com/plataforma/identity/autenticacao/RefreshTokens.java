package br.com.plataforma.identity.autenticacao;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.seguranca.TokenOpaco;

/**
 * Sessões abertas. O token vai para o cookie; aqui fica só o SHA-256 dele (ver {@link TokenOpaco}).
 *
 * Cada renovação grava uma linha nova e revoga a anterior; o sessao_id e o iniciada_em passam de
 * uma para a outra, e é por eles que a tela de sessões ativas identifica o dispositivo (RF06).
 */
@Repository
public class RefreshTokens {

    private final JdbcTemplate jdbc;

    public RefreshTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Registro(UUID id, UUID usuarioId, UUID sessaoId, Instant iniciadaEm, Instant expiraEm, boolean revogado) {

        public boolean valido() {
            return !revogado && expiraEm.isAfter(Instant.now());
        }
    }

    /** Linha ativa de uma sessão, para a tela "sessões ativas". */
    public record SessaoAtiva(UUID sessaoId, String ip, String userAgent, Instant iniciadaEm, Instant ultimaRenovacaoEm,
                              Instant expiraEm) {
    }

    /** Sessão nova, aberta pelo login. */
    public void criar(UUID usuario, String token, Instant expiraEm, Origem origem) {
        continuar(usuario, token, expiraEm, origem, UUID.randomUUID(), Instant.now());
    }

    /** Próximo elo de uma sessão que já existe: mesma sessão, mesmo início, mesmo fim. */
    public void continuar(UUID usuario, String token, Instant expiraEm, Origem origem, UUID sessaoId, Instant iniciadaEm) {
        jdbc.update("""
                INSERT INTO identity.refresh_tokens (id, usuario_id, token_hash, expira_em, ip, user_agent, sessao_id, iniciada_em)
                VALUES (?, ?, ?, ?, ?::inet, ?, ?, ?)
                """, UUID.randomUUID(), usuario, TokenOpaco.hash(token), utc(expiraEm), origem.ip(), origem.userAgent(),
                sessaoId, utc(iniciadaEm));
    }

    public Optional<Registro> buscar(String token) {
        return jdbc.query("""
                SELECT id, usuario_id, sessao_id, iniciada_em, expira_em, revogado_em IS NOT NULL AS revogado
                  FROM identity.refresh_tokens WHERE token_hash = ?
                """, (rs, linha) -> new Registro(
                        rs.getObject("id", UUID.class),
                        rs.getObject("usuario_id", UUID.class),
                        rs.getObject("sessao_id", UUID.class),
                        rs.getObject("iniciada_em", OffsetDateTime.class).toInstant(),
                        rs.getObject("expira_em", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("revogado")),
                TokenOpaco.hash(token)).stream().findFirst();
    }

    /** Sessão do cookie, se o cookie ainda vale e é deste usuário. */
    public Optional<UUID> sessaoDoCookie(String token, UUID usuario) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return buscar(token).filter(Registro::valido).filter(r -> r.usuarioId().equals(usuario)).map(Registro::sessaoId);
    }

    /** Uma linha por sessão: com a rotação, só a mais recente de cada sessão está sem revogação. */
    public List<SessaoAtiva> ativas(UUID usuario) {
        return jdbc.query("""
                SELECT sessao_id, host(ip) AS ip, user_agent, iniciada_em, created_at, expira_em
                  FROM identity.refresh_tokens
                 WHERE usuario_id = ? AND revogado_em IS NULL AND expira_em > now()
                 ORDER BY iniciada_em DESC
                """, (rs, linha) -> new SessaoAtiva(
                        rs.getObject("sessao_id", UUID.class),
                        rs.getString("ip"),
                        rs.getString("user_agent"),
                        rs.getObject("iniciada_em", OffsetDateTime.class).toInstant(),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("expira_em", OffsetDateTime.class).toInstant()),
                usuario);
    }

    /** @return false se outro pedido já revogou este token — só um deles pode rotacionar */
    public boolean revogar(UUID id) {
        return jdbc.update("UPDATE identity.refresh_tokens SET revogado_em = now() WHERE id = ? AND revogado_em IS NULL", id) == 1;
    }

    public int revogarTodos(UUID usuario) {
        return jdbc.update("UPDATE identity.refresh_tokens SET revogado_em = now() WHERE usuario_id = ? AND revogado_em IS NULL", usuario);
    }

    /** @return quantas linhas foram revogadas; zero se a sessão não existe, já acabou ou é de outro usuário */
    public int revogarSessao(UUID usuario, UUID sessao) {
        return jdbc.update("""
                UPDATE identity.refresh_tokens SET revogado_em = now()
                 WHERE usuario_id = ? AND sessao_id = ? AND revogado_em IS NULL
                """, usuario, sessao);
    }

    /** Todas as sessões do usuário menos uma — a que fez a operação. Sem ela, todas. */
    public int revogarOutras(UUID usuario, Optional<UUID> manter) {
        if (manter.isEmpty()) {
            return revogarTodos(usuario);
        }
        return jdbc.update("""
                UPDATE identity.refresh_tokens SET revogado_em = now()
                 WHERE usuario_id = ? AND sessao_id <> ? AND revogado_em IS NULL
                """, usuario, manter.get());
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
