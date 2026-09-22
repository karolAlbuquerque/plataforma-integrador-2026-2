package br.com.plataforma.identity.seguranca;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.plataforma.identity.tenant.TenantContexto;

/**
 * Quem está fazendo a operação de administração: o usuário do token, no tenant do token, com as
 * permissões do token. Token de serviço não administra nada — não representa uma pessoa.
 */
public record Ator(UUID id, UUID tenant, Set<String> permissoes) {

    public static Ator de(Jwt jwt) {
        if (Usuarios.ehServico(jwt)) {
            throw new AccessDeniedException("Token de serviço não representa um usuário.");
        }
        return new Ator(Usuarios.idDe(jwt), TenantContexto.exigir(), Set.copyOf(Usuarios.lista(jwt, "perms")));
    }

    /** Permissões da lista que o ator não tem, em ordem — vazio quando ele tem todas. */
    public Set<String> semAcessoA(Collection<String> exigidas) {
        Set<String> faltando = new TreeSet<>(exigidas);
        faltando.removeAll(permissoes);
        return faltando;
    }
}
