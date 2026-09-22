package br.com.plataforma.identity.senha;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.seguranca.TokenOpaco;

/**
 * Convite e recuperação de senha são o mesmo mecanismo, distinguidos pelo tipo (Modelo §6.7): um
 * token de uso único, com validade, enviado por e-mail. O banco guarda só o SHA-256 dele.
 *
 * "usado_em" preenchido encerra o link — por uso, por um link mais novo ou por desativação.
 */
@Repository
public class LinksDeSenha {

    public enum Tipo {
        CONVITE("convite"), RECUPERACAO("recuperacao");

        private final String valor;

        Tipo(String valor) {
            this.valor = valor;
        }

        public String valor() {
            return valor;
        }

        static Tipo de(String valor) {
            return valor.equals(CONVITE.valor) ? CONVITE : RECUPERACAO;
        }
    }

    /** O token só existe aqui, em memória, até ir para o e-mail. */
    public record LinkEmitido(String token, Instant expiraEm) {
    }

    public record Link(UUID id, UUID usuarioId, Tipo tipo, Instant expiraEm) {
    }

    private final JdbcTemplate jdbc;

    public LinksDeSenha(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Emite um link novo e encerra o pendente do mesmo tipo: vale sempre só o último. */
    public LinkEmitido emitir(UUID usuario, Tipo tipo, Duration validade) {
        encerrar(usuario, tipo);
        String token = TokenOpaco.gerar();
        Instant expiraEm = Instant.now().plus(validade);
        jdbc.update("""
                INSERT INTO identity.recuperacoes_senha (id, usuario_id, tipo, token_hash, expira_em)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), usuario, tipo.valor(), TokenOpaco.hash(token),
                OffsetDateTime.ofInstant(expiraEm, ZoneOffset.UTC));
        return new LinkEmitido(token, expiraEm);
    }

    public Optional<Link> valido(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT id, usuario_id, tipo, expira_em FROM identity.recuperacoes_senha
                 WHERE token_hash = ? AND usado_em IS NULL AND expira_em > now()
                """, (rs, linha) -> new Link(
                        rs.getObject("id", UUID.class),
                        rs.getObject("usuario_id", UUID.class),
                        Tipo.de(rs.getString("tipo")),
                        rs.getObject("expira_em", OffsetDateTime.class).toInstant()),
                TokenOpaco.hash(token)).stream().findFirst();
    }

    /** @return false se outro pedido usou o link antes ou se ele venceu no meio do caminho */
    public boolean consumir(UUID link) {
        return jdbc.update("""
                UPDATE identity.recuperacoes_senha SET usado_em = now()
                 WHERE id = ? AND usado_em IS NULL AND expira_em > now()
                """, link) == 1;
    }

    public void encerrar(UUID usuario, Tipo tipo) {
        jdbc.update("UPDATE identity.recuperacoes_senha SET usado_em = now() WHERE usuario_id = ? AND tipo = ? AND usado_em IS NULL",
                usuario, tipo.valor());
    }

    public void encerrarTodos(UUID usuario) {
        jdbc.update("UPDATE identity.recuperacoes_senha SET usado_em = now() WHERE usuario_id = ? AND usado_em IS NULL", usuario);
    }

    /** Validade do último convite ainda não usado, vencido ou não — para a tela mostrar. */
    public Optional<Instant> conviteExpiraEm(UUID usuario) {
        return jdbc.queryForList("""
                SELECT max(expira_em) FROM identity.recuperacoes_senha
                 WHERE usuario_id = ? AND tipo = 'convite' AND usado_em IS NULL
                """, OffsetDateTime.class, usuario).stream()
                .filter(Objects::nonNull).map(OffsetDateTime::toInstant).findFirst();
    }

    /** Quantos links deste tipo foram emitidos na janela — base do limite por e-mail. */
    public int emitidosDesde(UUID usuario, Tipo tipo, Instant desde) {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM identity.recuperacoes_senha
                 WHERE usuario_id = ? AND tipo = ? AND created_at > ?
                """, Integer.class, usuario, tipo.valor(), OffsetDateTime.ofInstant(desde, ZoneOffset.UTC));
        return total == null ? 0 : total;
    }
}
