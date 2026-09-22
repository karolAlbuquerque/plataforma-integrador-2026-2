package br.com.plataforma.identity.senha;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.MuitasTentativasException;
import br.com.plataforma.identity.api.NaoAutenticadoException;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.LimiteDeTentativas;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.autenticacao.RefreshTokens;
import br.com.plataforma.identity.senha.LinksDeSenha.Link;
import br.com.plataforma.identity.senha.LinksDeSenha.LinkEmitido;
import br.com.plataforma.identity.senha.LinksDeSenha.Tipo;
import br.com.plataforma.identity.usuario.UsuarioDeLogin;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;

/**
 * Recuperação de senha, definição pelo link de convite ou de recuperação e troca pelo próprio
 * usuário (Requisitos RF07, RF08, RF09 e RF14).
 */
@Service
public class SenhaServico {

    private static final Logger log = LoggerFactory.getLogger(SenhaServico.class);

    /** Entre dois pedidos de recuperação para o mesmo e-mail, e o máximo por hora. */
    private static final Duration INTERVALO_MINIMO = Duration.ofMinutes(1);
    private static final int MAXIMO_POR_HORA = 5;

    public record LinkVerificado(String tipo, String nome, String email, Instant expiraEm) {
    }

    private final UsuarioRepositorio usuarios;
    private final LinksDeSenha links;
    private final EnvioDeLinks envio;
    private final RefreshTokens refreshTokens;
    private final LimiteDeTentativas tentativas;
    private final LimiteDeRecuperacao limitePorIp;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Executor segundoPlano;
    private final Duration validadeDaRecuperacao;

    public SenhaServico(UsuarioRepositorio usuarios, LinksDeSenha links, EnvioDeLinks envio, RefreshTokens refreshTokens,
                        LimiteDeTentativas tentativas, LimiteDeRecuperacao limitePorIp, PasswordEncoder senhas,
                        Auditoria auditoria, TransactionTemplate transacao,
                        @Qualifier("tarefasDeEmail") Executor segundoPlano,
                        @Value("${identity.senha.validade-recuperacao}") Duration validadeDaRecuperacao) {
        this.usuarios = usuarios;
        this.links = links;
        this.envio = envio;
        this.refreshTokens = refreshTokens;
        this.tentativas = tentativas;
        this.limitePorIp = limitePorIp;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.segundoPlano = segundoPlano;
        this.validadeDaRecuperacao = validadeDaRecuperacao;
    }

    /**
     * Responde na hora, sempre do mesmo jeito: a busca do e-mail e o envio acontecem em segundo
     * plano, para que nem a resposta nem o tempo dela revelem se o e-mail existe (RF07).
     */
    public void pedirRecuperacao(String emailInformado, Origem origem) {
        String email = emailInformado.strip();
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        segundoPlano.execute(() -> {
            if (contexto != null) {
                MDC.setContextMap(contexto);
            }
            try {
                processarRecuperacao(email, origem);
            } catch (RuntimeException e) {
                log.error("Falha ao processar um pedido de recuperação de senha.", e);
            } finally {
                MDC.clear();
            }
        });
    }

    void processarRecuperacao(String email, Origem origem) {
        if (!limitePorIp.permitir(origem.ip())) {
            auditoria.registrar(null, null, origem.ip(), "recuperacao_ignorada", "senha", null,
                    Map.of("email", email, "motivo", "limite_por_ip"));
            return;
        }
        Optional<UsuarioDeLogin> encontrado = usuarios.buscarPorEmail(email).filter(UsuarioDeLogin::podeEntrar);
        if (encontrado.isEmpty()) {
            auditoria.registrar(null, null, origem.ip(), "recuperacao_ignorada", "senha", null,
                    Map.of("email", email, "motivo", "email_inexistente_ou_inativo"));
            return;
        }
        UsuarioDeLogin usuario = encontrado.get();
        LinkEmitido link = transacao.execute(status -> {
            Instant agora = Instant.now();
            if (links.emitidosDesde(usuario.id(), Tipo.RECUPERACAO, agora.minus(INTERVALO_MINIMO)) > 0
                    || links.emitidosDesde(usuario.id(), Tipo.RECUPERACAO, agora.minus(Duration.ofHours(1))) >= MAXIMO_POR_HORA) {
                auditoria.registrar(usuario.tenantId(), null, origem.ip(), "recuperacao_ignorada", "senha", usuario.id(),
                        Map.of("motivo", "limite_por_email"));
                return null;
            }
            LinkEmitido emitido = links.emitir(usuario.id(), Tipo.RECUPERACAO, validadeDaRecuperacao);
            auditoria.registrar(usuario.tenantId(), null, origem.ip(), "recuperacao_solicitada", "senha", usuario.id(), null);
            return emitido;
        });
        if (link != null) {
            envio.enviar(Tipo.RECUPERACAO, usuario.nome(), usuario.email(), usuario.tenantId(), link);
        }
    }

    /** Confere o link antes de mostrar o formulário. Não consome o token. */
    public LinkVerificado verificar(String token) {
        Link link = links.valido(token).orElseThrow(SenhaServico::linkInvalido);
        UsuarioDeLogin usuario = usuarios.buscarPorId(link.usuarioId()).filter(UsuarioDeLogin::podeEntrar)
                .orElseThrow(SenhaServico::linkInvalido);
        return new LinkVerificado(link.tipo().valor(), usuario.nome(), usuario.email(), link.expiraEm());
    }

    /**
     * Define a senha pelo link: consome o token, encerra os outros links do usuário e todas as
     * sessões abertas (RF07). Nenhum administrador conhece a senha em momento algum (RF14).
     */
    public void definir(String token, String novaSenha, Origem origem) {
        PoliticaDeSenha.exigir(novaSenha, "novaSenha");
        String hash = senhas.encode(novaSenha);   // BCrypt 12 é lento: fora da transação
        transacao.executeWithoutResult(status -> {
            Link link = links.valido(token).orElseThrow(SenhaServico::linkInvalido);
            UsuarioDeLogin usuario = usuarios.buscarPorId(link.usuarioId()).filter(UsuarioDeLogin::podeEntrar)
                    .orElseThrow(SenhaServico::linkInvalido);
            if (!links.consumir(link.id())) {
                throw linkInvalido();
            }
            usuarios.definirSenha(usuario.id(), hash, null);
            links.encerrarTodos(usuario.id());
            int encerradas = refreshTokens.revogarTodos(usuario.id());
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "definir_senha", "usuario", usuario.id(),
                    Map.of("via", link.tipo().valor(), "sessoesEncerradas", encerradas));
        });
    }

    /**
     * Troca pelo próprio usuário (RF08): exige a senha atual e mantém só a sessão do cookie que fez a
     * troca. Senha atual errada conta como tentativa de login — cinco em 15 minutos bloqueiam.
     */
    public void trocar(UUID usuarioId, String senhaAtual, String novaSenha, String cookieDeRefresh, Origem origem) {
        UsuarioDeLogin usuario = usuarios.buscarPorId(usuarioId).filter(UsuarioDeLogin::podeEntrar)
                .orElseThrow(() -> new NaoAutenticadoException("Sessão encerrada. Entre novamente."));
        if (tentativas.bloqueado(usuario.email(), origem.ip())) {
            throw new MuitasTentativasException("Muitas tentativas. Aguarde " + tentativas.janela().toMinutes() + " minutos.");
        }
        if (!confere(senhaAtual, usuario.senhaHash())) {
            tentativas.registrar(usuario.email(), origem.ip(), false);
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "trocar_senha_recusada", "usuario",
                    usuario.id(), Map.of("motivo", "senha_atual_incorreta"));
            throw ErroDeNegocio.regra("senhaAtual", "SENHA_ATUAL_INCORRETA", "A senha atual não confere.");
        }
        PoliticaDeSenha.exigir(novaSenha, "novaSenha");
        String hash = senhas.encode(novaSenha);
        transacao.executeWithoutResult(status -> {
            usuarios.definirSenha(usuario.id(), hash, usuario.id());
            links.encerrarTodos(usuario.id());
            int encerradas = refreshTokens.revogarOutras(usuario.id(),
                    refreshTokens.sessaoDoCookie(cookieDeRefresh, usuario.id()));
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "trocar_senha", "usuario", usuario.id(),
                    Map.of("sessoesEncerradas", encerradas));
        });
    }

    private boolean confere(String informada, String hash) {
        try {
            return hash != null && informada != null && senhas.matches(informada, hash);
        } catch (IllegalArgumentException e) {
            return false;   // acima de 72 bytes o BCrypt lança; aqui é só uma senha errada
        }
    }

    private static ErroDeNegocio linkInvalido() {
        return ErroDeNegocio.regra("token", "LINK_INVALIDO", "Este link é inválido ou já expirou. Peça um novo.");
    }
}
