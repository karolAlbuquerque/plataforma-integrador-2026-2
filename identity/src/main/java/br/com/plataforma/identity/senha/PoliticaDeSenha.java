package br.com.plataforma.identity.senha;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import br.com.plataforma.identity.api.ErroDeNegocio;

/**
 * Regra de senha nova (Requisito RF09): oito caracteres ou mais, com letra e número. O teto de 72
 * bytes é do BCrypt, que ignoraria o resto em silêncio. A mensagem nunca repete a senha.
 */
public final class PoliticaDeSenha {

    static final int MINIMO = 8;
    static final int MAXIMO_EM_BYTES = 72;

    private PoliticaDeSenha() {
    }

    public static Optional<String> problema(String senha) {
        if (senha == null || senha.codePointCount(0, senha.length()) < MINIMO) {
            return Optional.of("A senha precisa ter pelo menos " + MINIMO + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_EM_BYTES) {
            return Optional.of("A senha pode ter até " + MAXIMO_EM_BYTES + " bytes; use uma mais curta.");
        }
        if (senha.codePoints().noneMatch(Character::isLetter) || senha.codePoints().noneMatch(Character::isDigit)) {
            return Optional.of("A senha precisa ter letras e números.");
        }
        return Optional.empty();
    }

    public static void exigir(String senha, String campo) {
        problema(senha).ifPresent(motivo -> {
            throw ErroDeNegocio.invalido(campo, "SENHA_FRACA", motivo);
        });
    }
}
