package br.com.plataforma.identity.autenticacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

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

    /** tema: preferência guardada na conta (Requisito RF40) — claro, escuro ou sistema. */
    public record UsuarioResumo(UUID id, String nome, String email, UUID tenantId, String tema) {

        static UsuarioResumo de(UsuarioAutenticado usuario, String tema) {
            return new UsuarioResumo(usuario.id(), usuario.nome(), usuario.email(), usuario.tenantId(), tema);
        }
    }

    /**
     * sessaoExpiraEm: fim das oito horas da sessão, que a renovação não adia (Requisito RF41).
     * codigosRecuperacao: só na resposta que conclui o cadastro do segundo fator, a única vez em que
     * aparecem.
     */
    public record SessaoAberta(String accessToken, long expiraEmSegundos, Instant sessaoExpiraEm, UsuarioResumo usuario,
                               @JsonInclude(JsonInclude.Include.NON_NULL) List<String> codigosRecuperacao) {

        static SessaoAberta de(SessaoEmitida sessao) {
            return new SessaoAberta(sessao.token().valor(), sessao.token().validadeEmSegundos(), sessao.fimDaSessao(),
                    sessao.usuario(), sessao.codigosRecuperacao());
        }
    }

    /** O que o login pode devolver: a sessão, ou o pedido do segundo fator (Requisito RF10). */
    public sealed interface ResultadoDoLogin permits SessaoEmitida, DesafioEmitido {
    }

    /** O que o serviço devolve ao controller: o corpo da resposta e o que vai no cookie. */
    public record SessaoEmitida(TokenEmitido token, String refreshToken, Instant fimDaSessao, UsuarioResumo usuario,
                                List<String> codigosRecuperacao) implements ResultadoDoLogin {
    }

    /** Senha certa, falta o código: nada de cookie nem de access token ainda. */
    public record DesafioEmitido(String desafio, Instant expiraEm, boolean cadastro) implements ResultadoDoLogin {
    }

    /**
     * Resposta do login quando falta o segundo fator. etapa = "segundo_fator" (pedir o código do
     * aplicativo) ou "cadastro_segundo_fator" (primeiro acesso: mostrar o QR Code).
     */
    public record DesafioDeSegundoFator(String etapa, String desafio, Instant desafioExpiraEm) {

        static DesafioDeSegundoFator de(DesafioEmitido desafio) {
            return new DesafioDeSegundoFator(desafio.cadastro() ? "cadastro_segundo_fator" : "segundo_fator",
                    desafio.desafio(), desafio.expiraEm());
        }
    }

    /** Segunda etapa: o código do aplicativo ou, sem o celular, um código de recuperação. */
    public record CodigoDoDesafio(
            @NotBlank(message = "Informe o desafio.")
            @Size(max = 100, message = "Desafio inválido.")
            String desafio,

            @Size(max = 10, message = "O código tem seis dígitos.")
            String codigo,

            @Size(max = 20, message = "Código de recuperação inválido.")
            String codigoRecuperacao) {
    }

    public record DesafioDeCadastro(
            @NotBlank(message = "Informe o desafio.")
            @Size(max = 100, message = "Desafio inválido.")
            String desafio) {
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
