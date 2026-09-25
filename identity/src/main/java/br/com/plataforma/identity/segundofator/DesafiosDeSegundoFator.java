package br.com.plataforma.identity.segundofator;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.seguranca.TokenOpaco;

/**
 * O elo entre as duas etapas do login: a senha conferiu, falta o código. O token vai no corpo da
 * resposta (não em cookie) e o banco guarda só o hash, como nos demais tokens opacos.
 */
@Repository
public class DesafiosDeSegundoFator {

    /** Depois disso o desafio morre e o usuário volta à senha — e cada falha conta no limite do login. */
    static final int MAXIMO_DE_FALHAS = 5;

    public record Desafio(UUID id, UUID usuarioId, boolean cadastro, String segredoProvisorio) {
    }

    public record Emitido(String token, Instant expiraEm) {
    }

    private final JdbcTemplate jdbc;

    public DesafiosDeSegundoFator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Um desafio vivo por usuário: entrar de novo com a senha cancela o anterior. */
    public Emitido criar(UUID usuario, boolean cadastro, Duration validade) {
        jdbc.update("UPDATE identity.desafios_segundo_fator SET usado_em = now() WHERE usuario_id = ? AND usado_em IS NULL",
                usuario);
        String token = TokenOpaco.gerar();
        Instant expiraEm = Instant.now().plus(validade).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
                INSERT INTO identity.desafios_segundo_fator (id, usuario_id, token_hash, cadastro, expira_em)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), usuario, TokenOpaco.hash(token), cadastro, Timestamp.from(expiraEm));
        return new Emitido(token, expiraEm);
    }

    /** Desafio em vigor, travado até o fim da transação: dois códigos ao mesmo tempo não passam juntos. */
    public Optional<Desafio> buscarValido(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT id, usuario_id, cadastro, segredo_provisorio
                  FROM identity.desafios_segundo_fator
                 WHERE token_hash = ? AND usado_em IS NULL AND expira_em > now() AND falhas < ?
                   FOR UPDATE
                """, (rs, linha) -> new Desafio(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
                        rs.getBoolean("cadastro"), rs.getString("segredo_provisorio")),
                TokenOpaco.hash(token), MAXIMO_DE_FALHAS).stream().findFirst();
    }

    public void registrarFalha(UUID id) {
        jdbc.update("UPDATE identity.desafios_segundo_fator SET falhas = falhas + 1 WHERE id = ?", id);
    }

    public void guardarSegredo(UUID id, String segredoCifrado) {
        jdbc.update("UPDATE identity.desafios_segundo_fator SET segredo_provisorio = ? WHERE id = ?", segredoCifrado, id);
    }

    /** Uso único: depois de abrir a sessão, o mesmo desafio não abre outra. */
    public void consumir(UUID id) {
        jdbc.update("UPDATE identity.desafios_segundo_fator SET usado_em = now(), segredo_provisorio = NULL WHERE id = ?", id);
    }
}
