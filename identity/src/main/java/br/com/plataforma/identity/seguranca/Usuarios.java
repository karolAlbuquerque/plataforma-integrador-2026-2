package br.com.plataforma.identity.seguranca;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/** Leitura do usuário a partir do token. */
public final class Usuarios {

    private Usuarios() {
    }

    /** Token emitido para rotina sem usuário (Contrato §9.2). */
    public static boolean ehServico(Jwt jwt) {
        String sub = jwt.getSubject();
        return sub != null && sub.startsWith("svc:");
    }

    /** Id do usuário; nulo quando quem chama é um serviço. */
    public static UUID idDe(Jwt jwt) {
        return ehServico(jwt) ? null : UUID.fromString(jwt.getSubject());
    }

    /** Lista de texto do token; ausente vira lista vazia. */
    public static List<String> lista(Jwt jwt, String claim) {
        List<String> valor = jwt.getClaimAsStringList(claim);
        return valor == null ? List.of() : valor;
    }
}
