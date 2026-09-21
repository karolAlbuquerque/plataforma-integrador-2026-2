package br.com.plataforma.identity.autenticacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.plataforma.identity.seguranca.TokenEmitido;
import br.com.plataforma.identity.usuario.UsuarioAutenticado;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos de entrada e saída das rotas de sessão, como em contratos/identity.yaml. */
public final class Formatos {

    private Formatos() {
    }

    /** Sem tamanho mínimo de senha aqui: no login, uma senha curta é só uma senha errada. */
    public record Credenciais(
            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail pode ter até 254 caracteres.")
            String email,

            @NotBlank(message = "Informe a senha.")
            @Size(max = 72, message = "A senha pode ter até 72 caracteres.")
            String senha) {
    }

    public record UsuarioResumo(UUID id, String nome, String email, UUID tenantId) {

        static UsuarioResumo de(UsuarioAutenticado usuario) {
            return new UsuarioResumo(usuario.id(), usuario.nome(), usuario.email(), usuario.tenantId());
        }
    }

    public record SessaoAberta(String accessToken, long expiraEmSegundos, UsuarioResumo usuario) {

        static SessaoAberta de(SessaoEmitida sessao) {
            return new SessaoAberta(sessao.token().valor(), sessao.token().validadeEmSegundos(), sessao.usuario());
        }
    }

    /** O que o serviço devolve ao controller: o corpo da resposta e o que vai no cookie. */
    public record SessaoEmitida(TokenEmitido token, String refreshToken, Instant fimDaSessao, UsuarioResumo usuario) {
    }

    public record TenantResumo(UUID id, String nome) {
    }

    public record Eu(UsuarioResumo usuario, TenantResumo tenant, List<String> perfis, List<String> permissoes,
                     List<UUID> equipes) {
    }

    public record CredencialDeServico(
            @NotBlank(message = "Informe o clientId.")
            @Size(max = 30, message = "O clientId pode ter até 30 caracteres.")
            String clientId,

            @NotBlank(message = "Informe o clientSecret.")
            @Size(max = 200, message = "O clientSecret pode ter até 200 caracteres.")
            String clientSecret) {
    }

    public record TokenDeServico(String accessToken, long expiresIn) {
    }
}
