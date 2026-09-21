package br.com.plataforma.identity.usuario;

import java.util.List;
import java.util.UUID;

/** Tudo o que vai no token de um usuário: identidade, perfis, permissões e equipes. */
public record UsuarioAutenticado(
        UUID id,
        UUID tenantId,
        String nome,
        String email,
        List<String> perfis,
        List<String> permissoes,
        List<UUID> equipes) {
}
