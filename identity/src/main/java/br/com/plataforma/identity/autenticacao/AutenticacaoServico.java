package br.com.plataforma.identity.autenticacao;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.api.MuitasTentativasException;
import br.com.plataforma.identity.api.NaoAutenticadoException;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.CredenciaisDeServico.Credencial;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoEmitida;
import br.com.plataforma.identity.autenticacao.Formatos.UsuarioResumo;
import br.com.plataforma.identity.seguranca.EmissorDeToken;
import br.com.plataforma.identity.seguranca.TokenEmitido;
import br.com.plataforma.identity.seguranca.TokenOpaco;
import br.com.plataforma.identity.usuario.UsuarioAutenticado;
import br.com.plataforma.identity.usuario.UsuarioDeLogin;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;

/**
 * Login, renovação, logout e token de serviço (Requisitos RF01 a RF05 e RF12).
 *
 * As falhas de login são gravadas fora de transação, de propósito: a exceção que responde 401
 * não pode desfazer o registro da tentativa, senão o limite de tentativas nunca dispararia.
 */
@Service
public class AutenticacaoServico {

    static final String CREDENCIAL_INVALIDA = "E-mail ou senha incorretos.";
    static final String CREDENCIAL_DE_SERVICO_INVALIDA = "Credencial de serviço inválida.";

    private final UsuarioRepositorio usuarios;
    private final LimiteDeTentativas tentativas;
    private final RefreshTokens refreshTokens;
    private final CredenciaisDeServico credenciais;
    private final EmissorDeToken emissor;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Duration duracaoMaximaDaSessao;

    /** Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo nos dois casos. */
    private final String hashDeComparacao;

    public AutenticacaoServico(UsuarioRepositorio usuarios, LimiteDeTentativas tentativas, RefreshTokens refreshTokens,
                               CredenciaisDeServico credenciais, EmissorDeToken emissor, PasswordEncoder senhas,
                               Auditoria auditoria, TransactionTemplate transacao,
                               @Value("${identity.sessao.duracao-maxima}") Duration duracaoMaximaDaSessao) {
        this.usuarios = usuarios;
        this.tentativas = tentativas;
        this.refreshTokens = refreshTokens;
        this.credenciais = credenciais;
        this.emissor = emissor;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.duracaoMaximaDaSessao = duracaoMaximaDaSessao;
        this.hashDeComparacao = senhas.encode(UUID.randomUUID().toString());
    }

    public SessaoEmitida entrar(String emailInformado, String senha, Origem origem) {
        String email = emailInformado.strip();

        // O bloqueio vem antes da senha: bloqueado, nem a senha certa entra
        if (tentativas.bloqueado(email, origem.ip())) {
            auditoria.registrar(null, null, origem.ip(), "login_bloqueado", "sessao", null, Map.of("email", email));
            throw new MuitasTentativasException(
                    "Muitas tentativas. Aguarde " + tentativas.janela().toMinutes() + " minutos.");
        }

        Optional<UsuarioDeLogin> encontrado = usuarios.buscarPorEmail(email);
        String hash = encontrado.map(UsuarioDeLogin::senhaHash).orElse(hashDeComparacao);
        boolean senhaConfere = confere(senha, hash) && encontrado.map(u -> u.senhaHash() != null).orElse(false);

        String motivo = encontrado.isEmpty() ? "email_inexistente"
                : encontrado.get().senhaHash() == null ? "sem_senha_definida"
                : !senhaConfere ? "senha_incorreta"
                : !encontrado.get().ativo() ? "usuario_inativo"
                : !encontrado.get().tenantAtivo() ? "tenant_inativo"
                : null;

        if (motivo != null) {
            tentativas.registrar(email, origem.ip(), false);
            auditoria.registrar(encontrado.map(UsuarioDeLogin::tenantId).orElse(null),
                    encontrado.map(UsuarioDeLogin::id).orElse(null), origem.ip(), "login_falhou", "sessao",
                    encontrado.map(UsuarioDeLogin::id).orElse(null), Map.of("email", email, "motivo", motivo));
            // A mesma resposta para todos os motivos: não revela se o e-mail existe
            throw new NaoAutenticadoException(CREDENCIAL_INVALIDA);
        }

        UsuarioDeLogin usuario = encontrado.get();
        tentativas.registrar(email, origem.ip(), true);
        return transacao.execute(status -> {
            usuarios.registrarLogin(usuario.id());
            UsuarioAutenticado acessos = usuarios.carregarAcessos(usuario);
            String refresh = TokenOpaco.gerar();
            Instant fimDaSessao = Instant.now().plus(duracaoMaximaDaSessao);
            refreshTokens.criar(usuario.id(), refresh, fimDaSessao, origem);
            SessaoEmitida sessao = new SessaoEmitida(emissor.paraUsuario(acessos), refresh, fimDaSessao,
                    UsuarioResumo.de(acessos));
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "login", "sessao", usuario.id(), null);
            return sessao;
        });
    }

    /**
     * Troca o refresh token por um novo, com o mesmo fim de sessão, e emite um access token com as
     * permissões lidas de novo do banco. Token revogado, expirado ou desconhecido: vazio.
     */
    public Optional<SessaoEmitida> renovar(String refreshToken, Origem origem) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return transacao.execute(status -> {
            Optional<RefreshTokens.Registro> registro = refreshTokens.buscar(refreshToken);
            if (registro.isEmpty()) {
                return Optional.empty();
            }
            RefreshTokens.Registro atual = registro.get();
            if (atual.revogado()) {
                // Reapresentar um token já trocado pode ser cópia roubada do cookie
                auditoria.registrar(null, atual.usuarioId(), origem.ip(), "refresh_reutilizado", "sessao",
                        atual.usuarioId(), null);
                return Optional.empty();
            }
            if (!atual.valido() || !refreshTokens.revogar(atual.id())) {
                return Optional.empty();
            }
            Optional<UsuarioDeLogin> usuario = usuarios.buscarPorId(atual.usuarioId()).filter(UsuarioDeLogin::podeEntrar);
            if (usuario.isEmpty()) {
                return Optional.empty();   // desativado durante a sessão: o token revogado acima encerra
            }
            UsuarioAutenticado acessos = usuarios.carregarAcessos(usuario.get());
            String refresh = TokenOpaco.gerar();
            refreshTokens.continuar(acessos.id(), refresh, atual.expiraEm(), origem, atual.sessaoId(), atual.iniciadaEm());
            return Optional.of(new SessaoEmitida(emissor.paraUsuario(acessos), refresh, atual.expiraEm(),
                    UsuarioResumo.de(acessos)));
        });
    }

    /** Revoga a sessão do cookie, ou todas as do usuário. Sempre responde sucesso. */
    public void sair(String refreshToken, boolean todas, Origem origem) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokens.buscar(refreshToken).ifPresent(registro -> {
            if (todas && registro.valido()) {
                refreshTokens.revogarTodos(registro.usuarioId());
            } else {
                refreshTokens.revogar(registro.id());
            }
            usuarios.buscarPorId(registro.usuarioId()).ifPresent(usuario ->
                    auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(),
                            todas ? "logout_todas" : "logout", "sessao", usuario.id(), null));
        });
    }

    /** Token para rotina sem usuário. Emissão e recusa são auditadas, com tenant nulo (§9.2). */
    public TokenEmitido emitirTokenDeServico(String clientId, String segredo, Origem origem) {
        Optional<Credencial> credencial = credenciais.buscarAtiva(clientId);
        boolean confere = confere(segredo, credencial.map(Credencial::segredoHash).orElse(hashDeComparacao))
                && credencial.isPresent();
        UUID id = credencial.map(Credencial::id).orElse(null);
        if (!confere) {
            auditoria.registrar(null, null, origem.ip(), "token_servico_recusado", "credencial_servico", id,
                    Map.of("clientId", clientId));
            throw new NaoAutenticadoException(CREDENCIAL_DE_SERVICO_INVALIDA);
        }
        TokenEmitido token = emissor.paraServico(clientId, credenciais.permissoes(id));
        auditoria.registrar(null, null, origem.ip(), "token_servico", "credencial_servico", id, Map.of("clientId", clientId));
        return token;
    }

    /** O BCrypt recusa entrada acima de 72 bytes com exceção; aqui isso é só uma senha errada. */
    private boolean confere(String informado, String hash) {
        try {
            return hash != null && senhas.matches(informado, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
