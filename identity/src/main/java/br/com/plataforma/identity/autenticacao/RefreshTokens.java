package br.com.plataforma.identity.autenticacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Sessões abertas. O token vai para o cookie; aqui fica só o SHA-256 dele — quem ler a tabela
 * não consegue se passar por ninguém.
 */
@Repository
public class RefreshTokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcTemplate jdbc;

    public RefreshTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Registro(UUID id, UUID usuarioId, Instant expiraEm, boolean revogado) {

        public boolean valido() {
            return !revogado && expiraEm.isAfter(Instant.now());
        }
    }

    /** 256 bits aleatórios, em base64 sem padding — seguro para valor de cookie. */
    public static String gerar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void criar(UUID usuario, String token, Instant expiraEm, Origem origem) {
        jdbc.update("""
                INSERT INTO identity.refresh_tokens (id, usuario_id, token_hash, expira_em, ip, user_agent)
                VALUES (?, ?, ?, ?, ?::inet, ?)
                """, UUID.randomUUID(), usuario, hash(token), OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC),
                origem.ip(), origem.userAgent());
    }

    public Optional<Registro> buscar(String token) {
        return jdbc.query("""
                SELECT id, usuario_id, expira_em, revogado_em IS NOT NULL AS revogado
                  FROM identity.refresh_tokens WHERE token_hash = ?
                """, (rs, linha) -> new Registro(
                        rs.getObject("id", UUID.class),
                        rs.getObject("usuario_id", UUID.class),
                        rs.getObject("expira_em", OffsetDateTime.class).toInstant(),
                        rs.getBoolean("revogado")),
                hash(token)).stream().findFirst();
    }

    /** @return false se outro pedido já revogou este token — só um deles pode rotacionar */
    public boolean revogar(UUID id) {
        return jdbc.update("UPDATE identity.refresh_tokens SET revogado_em = now() WHERE id = ? AND revogado_em IS NULL", id) == 1;
    }

    public int revogarTodos(UUID usuario) {
        return jdbc.update("UPDATE identity.refresh_tokens SET revogado_em = now() WHERE usuario_id = ? AND revogado_em IS NULL", usuario);
    }

    static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
