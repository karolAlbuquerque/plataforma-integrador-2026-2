package br.com.plataforma.identity.segundofator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.plataforma.identity.seguranca.TokenOpaco;

/**
 * Dez códigos de uso único para quem perdeu o celular. Aparecem uma vez só, na hora em que são
 * gerados; o banco guarda o SHA-256 de cada um, junto do id do usuário (o mesmo código em dois
 * usuários dá hashes diferentes).
 */
@Repository
public class CodigosDeRecuperacao {

    static final int QUANTIDADE = 10;
    /** Sem 0/O e 1/I/L: o usuário copia do papel. 31 símbolos em 10 posições, quase 50 bits. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcTemplate jdbc;

    public CodigosDeRecuperacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Troca os códigos do usuário por dez novos; os anteriores deixam de valer. */
    public List<String> gerarNovos(UUID usuario) {
        jdbc.update("DELETE FROM identity.codigos_recuperacao WHERE usuario_id = ?", usuario);
        List<String> codigos = new ArrayList<>(QUANTIDADE);
        for (int i = 0; i < QUANTIDADE; i++) {
            String codigo = sortear();
            codigos.add(codigo);
            jdbc.update("INSERT INTO identity.codigos_recuperacao (id, usuario_id, hash) VALUES (?, ?, ?)",
                    UUID.randomUUID(), usuario, hash(usuario, codigo));
        }
        return codigos;
    }

    /** Marca o código como usado. O UPDATE condicional garante uso único mesmo com dois pedidos juntos. */
    public boolean usar(UUID usuario, String informado) {
        if (informado == null || normalizar(informado).length() != 10) {
            return false;
        }
        return jdbc.update("""
                UPDATE identity.codigos_recuperacao SET usado_em = now()
                 WHERE usuario_id = ? AND hash = ? AND usado_em IS NULL
                """, usuario, hash(usuario, informado)) == 1;
    }

    public int restantes(UUID usuario) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM identity.codigos_recuperacao WHERE usuario_id = ? AND usado_em IS NULL",
                Integer.class, usuario);
        return total == null ? 0 : total;
    }

    public void apagar(UUID usuario) {
        jdbc.update("DELETE FROM identity.codigos_recuperacao WHERE usuario_id = ?", usuario);
    }

    /** ABCDE-FGHJK: o hífen e as minúsculas são só apresentação. */
    private static String sortear() {
        StringBuilder codigo = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                codigo.append('-');
            }
            codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    private static String normalizar(String codigo) {
        return codigo.replaceAll("[\\s-]", "").toUpperCase();
    }

    private static String hash(UUID usuario, String codigo) {
        return TokenOpaco.hash(usuario + ":" + normalizar(codigo));
    }
}
