package br.com.plataforma.identity.autenticacao;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.api.ErroCampo;
import br.com.plataforma.identity.api.MuitasTentativasException;
import br.com.plataforma.identity.api.NaoAutenticadoException;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.CredenciaisDeServico.Credencial;
import br.com.plataforma.identity.autenticacao.Formatos.DesafioEmitido;
import br.com.plataforma.identity.autenticacao.Formatos.ResultadoDoLogin;
import br.com.plataforma.identity.autenticacao.Formatos.SessaoEmitida;
import br.com.plataforma.identity.autenticacao.Formatos.UsuarioResumo;
import br.com.plataforma.identity.segundofator.AvisosDeSegundoFator;
import br.com.plataforma.identity.segundofator.DesafiosDeSegundoFator;
import br.com.plataforma.identity.segundofator.DesafiosDeSegundoFator.Desafio;
import br.com.plataforma.identity.segundofator.SegundoFator;
import br.com.plataforma.identity.segundofator.SegundoFator.Cadastro;
import br.com.plataforma.identity.seguranca.EmissorDeToken;
import br.com.plataforma.identity.seguranca.TokenEmitido;
import br.com.plataforma.identity.seguranca.TokenOpaco;
import br.com.plataforma.identity.usuario.UsuarioAutenticado;
import br.com.plataforma.identity.usuario.UsuarioDeLogin;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;

/**
 * Login, renovação, logout e token de serviço (Requisitos RF01 a RF05 e RF12), com o segundo fator
 * (RF10): senha certa e segundo fator ativo — ou obrigatório e ainda não cadastrado — devolve um
 * desafio em vez da sessão, e a sessão só sai com o código.
 *
 * As falhas de login são gravadas fora de transação, de propósito: a exceção que responde 401
 * não pode desfazer o registro da tentativa, senão o limite de tentativas nunca dispararia. Pelo
 * mesmo motivo as etapas do segundo fator devolvem o resultado da transação e só depois lançam.
 */
@Service
public class AutenticacaoServico {

    static final String CREDENCIAL_INVALIDA = "E-mail ou senha incorretos.";
    static final String CREDENCIAL_DE_SERVICO_INVALIDA = "Credencial de serviço inválida.";
    static final String CODIGO_INVALIDO = "Código inválido.";
    static final String DESAFIO_EXPIRADO = "A verificação expirou. Entre de novo com a sua senha.";

    /** Como terminou a conferência do código, decidido dentro da transação e tratado fora dela. */
    private enum Falha { DESAFIO_EXPIRADO, BLOQUEADO, CODIGO_INVALIDO }

    private record Conferencia(Falha falha, UsuarioDeLogin usuario, SessaoEmitida sessao, boolean usouRecuperacao) {

        static Conferencia falhou(Falha falha, UsuarioDeLogin usuario) {
            return new Conferencia(falha, usuario, null, false);
        }
    }

    private final UsuarioRepositorio usuarios;
    private final LimiteDeTentativas tentativas;
    private final RefreshTokens refreshTokens;
    private final CredenciaisDeServico credenciais;
    private final EmissorDeToken emissor;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final SegundoFator segundoFator;
    private final DesafiosDeSegundoFator desafios;
    private final AvisosDeSegundoFator avisos;
    private final Duration duracaoMaximaDaSessao;
    private final Duration validadeDoDesafio;

    /** Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo nos dois casos. */
    private final String hashDeComparacao;

    public AutenticacaoServico(UsuarioRepositorio usuarios, LimiteDeTentativas tentativas, RefreshTokens refreshTokens,
                               CredenciaisDeServico credenciais, EmissorDeToken emissor, PasswordEncoder senhas,
                               Auditoria auditoria, TransactionTemplate transacao, SegundoFator segundoFator,
                               DesafiosDeSegundoFator desafios, AvisosDeSegundoFator avisos,
                               @Value("${identity.sessao.duracao-maxima}") Duration duracaoMaximaDaSessao,
                               @Value("${identity.segundo-fator.validade-desafio}") Duration validadeDoDesafio) {
        this.usuarios = usuarios;
        this.tentativas = tentativas;
        this.refreshTokens = refreshTokens;
        this.credenciais = credenciais;
        this.emissor = emissor;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.segundoFator = segundoFator;
        this.desafios = desafios;
        this.avisos = avisos;
        this.duracaoMaximaDaSessao = duracaoMaximaDaSessao;
        this.validadeDoDesafio = validadeDoDesafio;
        this.hashDeComparacao = senhas.encode(UUID.randomUUID().toString());
    }

    public ResultadoDoLogin entrar(String emailInformado, String senha, Origem origem) {
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
        if (usuario.segundoFatorAtivo() || segundoFator.obrigatorio()) {
            boolean cadastro = !usuario.segundoFatorAtivo();
            DesafiosDeSegundoFator.Emitido desafio = transacao.execute(status ->
                    desafios.criar(usuario.id(), cadastro, validadeDoDesafio));
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "login_senha_conferida", "sessao",
                    usuario.id(), Map.of("etapa", cadastro ? "cadastro_segundo_fator" : "segundo_fator"));
            return new DesafioEmitido(desafio.token(), desafio.expiraEm(), cadastro);
        }
        return transacao.execute(status -> abrirSessao(usuario, origem, null, Map.of()));
    }

    /** Segunda etapa: o código do aplicativo ou um código de recuperação abre a sessão. */
    public SessaoEmitida concluirComCodigo(String token, String codigo, String codigoRecuperacao, Origem origem) {
        boolean porRecuperacao = (codigo == null || codigo.isBlank()) && codigoRecuperacao != null;
        Conferencia resultado = transacao.execute(status -> {
            Optional<Desafio> desafio = desafios.buscarValido(token).filter(d -> !d.cadastro());
            Optional<UsuarioDeLogin> usuario = desafio.flatMap(d -> usuarios.buscarPorId(d.usuarioId()))
                    .filter(u -> u.podeEntrar() && u.segundoFatorAtivo());
            if (usuario.isEmpty()) {
                desafio.ifPresent(d -> desafios.consumir(d.id()));
                return Conferencia.falhou(Falha.DESAFIO_EXPIRADO, null);
            }
            UsuarioDeLogin u = usuario.get();
            if (tentativas.bloqueado(u.email(), origem.ip())) {
                return Conferencia.falhou(Falha.BLOQUEADO, u);
            }
            boolean confere = porRecuperacao
                    ? segundoFator.usarCodigoDeRecuperacao(u.id(), codigoRecuperacao)
                    : segundoFator.conferir(u.id(), codigo);
            if (!confere) {
                desafios.registrarFalha(desafio.get().id());
                return Conferencia.falhou(Falha.CODIGO_INVALIDO, u);
            }
            desafios.consumir(desafio.get().id());
            return new Conferencia(null, u, abrirSessao(u, origem, null,
                    Map.of("segundoFator", porRecuperacao ? "codigo_recuperacao" : "aplicativo")), porRecuperacao);
        });
        UsuarioDeLogin usuario = tratarFalha(resultado, origem);
        if (resultado.usouRecuperacao()) {
            int restantes = segundoFator.situacao(usuario.id()).codigosRestantes();
            auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "codigo_recuperacao_usado", "segundo_fator",
                    usuario.id(), Map.of("restantes", restantes));
            avisos.codigoUsado(usuario.nome(), usuario.email(), usuario.tenantId(), restantes);
        }
        return resultado.sessao();
    }

    /**
     * Primeiro acesso com o segundo fator obrigatório: gera o segredo que vira QR Code na tela. Pedir
     * de novo (a tela recarregou) devolve o mesmo segredo, enquanto o desafio valer.
     */
    public Cadastro iniciarCadastro(String token) {
        return transacao.execute(status -> {
            Desafio desafio = desafios.buscarValido(token).filter(Desafio::cadastro)
                    .orElseThrow(AutenticacaoServico::desafioExpirado);
            UsuarioDeLogin usuario = usuarios.buscarPorId(desafio.usuarioId()).filter(UsuarioDeLogin::podeEntrar)
                    .orElseThrow(AutenticacaoServico::desafioExpirado);
            if (desafio.segredoProvisorio() != null) {
                return segundoFator.cadastroDe(usuario.email(), desafio.segredoProvisorio());
            }
            SegundoFator.SegredoNovo novo = segundoFator.novoSegredo(usuario.email());
            desafios.guardarSegredo(desafio.id(), novo.cifrado());
            return novo.cadastro();
        });
    }

    /** O primeiro código confere: ativa o segundo fator, abre a sessão e devolve os códigos de recuperação. */
    public SessaoEmitida confirmarCadastro(String token, String codigo, Origem origem) {
        Conferencia resultado = transacao.execute(status -> {
            Optional<Desafio> desafio = desafios.buscarValido(token)
                    .filter(d -> d.cadastro() && d.segredoProvisorio() != null);
            Optional<UsuarioDeLogin> usuario = desafio.flatMap(d -> usuarios.buscarPorId(d.usuarioId()))
                    .filter(UsuarioDeLogin::podeEntrar);
            if (usuario.isEmpty()) {
                return Conferencia.falhou(Falha.DESAFIO_EXPIRADO, null);
            }
            UsuarioDeLogin u = usuario.get();
            if (tentativas.bloqueado(u.email(), origem.ip())) {
                return Conferencia.falhou(Falha.BLOQUEADO, u);
            }
            OptionalLong passo = segundoFator.conferirProvisorio(desafio.get().segredoProvisorio(), codigo);
            if (passo.isEmpty()) {
                desafios.registrarFalha(desafio.get().id());
                return Conferencia.falhou(Falha.CODIGO_INVALIDO, u);
            }
            List<String> codigos = segundoFator.ativar(u.id(), desafio.get().segredoProvisorio(), passo.getAsLong());
            desafios.consumir(desafio.get().id());
            auditoria.registrar(u.tenantId(), u.id(), origem.ip(), "ativar", "segundo_fator", u.id(), null);
            return new Conferencia(null, u, abrirSessao(u, origem, codigos, Map.of("segundoFator", "cadastro")), false);
        });
        UsuarioDeLogin usuario = tratarFalha(resultado, origem);
        avisos.ativado(usuario.nome(), usuario.email(), usuario.tenantId());
        return resultado.sessao();
    }

    /** Grava a falha fora da transação e responde; sem falha, devolve o usuário que entrou. */
    private UsuarioDeLogin tratarFalha(Conferencia resultado, Origem origem) {
        if (resultado.falha() == null) {
            return resultado.usuario();
        }
        UsuarioDeLogin usuario = resultado.usuario();
        switch (resultado.falha()) {
            case DESAFIO_EXPIRADO -> throw desafioExpirado();
            case BLOQUEADO -> {
                auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "login_bloqueado", "sessao",
                        usuario.id(), Map.of("email", usuario.email()));
                throw new MuitasTentativasException(
                        "Muitas tentativas. Aguarde " + tentativas.janela().toMinutes() + " minutos.");
            }
            default -> {
                tentativas.registrar(usuario.email(), origem.ip(), false);
                auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "segundo_fator_falhou", "sessao",
                        usuario.id(), null);
                throw new NaoAutenticadoException(CODIGO_INVALIDO,
                        new ErroCampo("codigo", "CODIGO_INVALIDO", CODIGO_INVALIDO));
            }
        }
    }

    private static NaoAutenticadoException desafioExpirado() {
        return new NaoAutenticadoException(DESAFIO_EXPIRADO,
                new ErroCampo("desafio", "DESAFIO_EXPIRADO", DESAFIO_EXPIRADO));
    }

    /** Espera a transação aberta por quem chama. */
    private SessaoEmitida abrirSessao(UsuarioDeLogin usuario, Origem origem, List<String> codigosRecuperacao,
                                      Map<String, Object> detalhes) {
        usuarios.registrarLogin(usuario.id());
        UsuarioAutenticado acessos = usuarios.carregarAcessos(usuario);
        String refresh = TokenOpaco.gerar();
        // Em segundos: o banco guarda microssegundos, e a renovação devolve o valor lido de lá
        Instant fimDaSessao = Instant.now().plus(duracaoMaximaDaSessao).truncatedTo(ChronoUnit.SECONDS);
        refreshTokens.criar(usuario.id(), refresh, fimDaSessao, origem);
        SessaoEmitida sessao = new SessaoEmitida(emissor.paraUsuario(acessos), refresh, fimDaSessao,
                UsuarioResumo.de(acessos, usuario.tema()), codigosRecuperacao);
        auditoria.registrar(usuario.tenantId(), usuario.id(), origem.ip(), "login", "sessao", usuario.id(),
                detalhes.isEmpty() ? null : detalhes);
        return sessao;
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
                    UsuarioResumo.de(acessos, usuario.get().tema()), null));
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
