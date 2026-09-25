package br.com.plataforma.identity.usuario;

import java.util.UUID;

/** Usuário como o login o enxerga, antes de haver tenant no contexto. */
public record UsuarioDeLogin(
        UUID id,
        UUID tenantId,
        String nome,
        String email,
        String senhaHash,
        boolean ativo,
        boolean tenantAtivo,
        boolean segundoFatorAtivo,
        String tema) {

    /** Usuário desativado ou de tenant desativado não entra, nem renova sessão (RF27). */
    public boolean podeEntrar() {
        return ativo && tenantAtivo;
    }
}
