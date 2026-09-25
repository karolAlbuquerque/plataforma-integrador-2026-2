package br.com.plataforma.identity.usuario;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.acesso.RegrasDeAcesso;
import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.autenticacao.RefreshTokens;
import br.com.plataforma.identity.mensageria.PublicadorDeEventos;
import br.com.plataforma.identity.mensageria.PublicadorDeEventos.DadosUsuarioAnonimizado;
import br.com.plataforma.identity.senha.LinksDeSenha;
import br.com.plataforma.identity.seguranca.Ator;

/**
 * Direitos do titular na LGPD (Requisito RF56): acesso aos dados pessoais (art. 18, II) e
 * anonimização (art. 18, IV).
 *
 * A exportação junta o que o identity guarda da pessoa: cadastro, perfis, equipes, sessões,
 * tentativas de login, notificações e o que ela fez segundo a auditoria.
 *
 * A anonimização troca nome, e-mail e telefone por marcadores e apaga senha, segundo fator,
 * sessões, links, notificações e tentativas de login. O id fica: a autoria dos registros dos outros
 * módulos continua apontando para "Usuário anonimizado". A auditoria não muda — o banco proíbe
 * UPDATE em audit_logs, e o registro de acesso é obrigação legal (art. 16, I, e Marco Civil art. 15).
 * Só de usuário já desativado, e só por quem tem identity.usuario.dados_pessoais (decisão de 25/09).
 */
@Service
public class DadosPessoais {

    public static final String NOME_ANONIMIZADO = "Usuário anonimizado";

    public record Cadastro(UUID id, String nome, String email, String telefone, String tema, boolean ativo,
                           boolean segundoFatorAtivo, Instant criadoEm, Instant ultimoLoginEm, Instant anonimizadoEm) {
    }

    public record Sessao(UUID sessaoId, String ip, String navegador, Instant iniciadaEm, Instant expiraEm,
                         Instant encerradaEm) {
    }

    public record TentativaDeLogin(Instant em, String ip, boolean sucesso) {
    }

    public record Notificacao(Instant em, String categoria, String titulo, String texto, Instant lidaEm) {
    }

    public record AcaoRegistrada(Instant em, String acao, String entidade, UUID entidadeId, String ip) {
    }

    public record Exportacao(Instant geradoEm, Cadastro cadastro, List<String> perfis, List<String> equipes,
                             List<Sessao> sessoes, List<TentativaDeLogin> tentativasDeLogin,
                             List<Notificacao> notificacoes, List<AcaoRegistrada> acoes) {
    }

    private record Situacao(String email, boolean ativo, boolean anonimizado) {
    }

    private final JdbcTemplate jdbc;
    private final RegrasDeAcesso regras;
    private final RefreshTokens refreshTokens;
    private final LinksDeSenha links;
    private final Auditoria auditoria;
    private final PublicadorDeEventos eventos;
    private final TransactionTemplate transacao;

    public DadosPessoais(JdbcTemplate jdbc, RegrasDeAcesso regras, RefreshTokens refreshTokens, LinksDeSenha links,
                         Auditoria auditoria, PublicadorDeEventos eventos, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.regras = regras;
        this.refreshTokens = refreshTokens;
        this.links = links;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.transacao = transacao;
    }

    /** Auditado: exportar dados de alguém é acesso a dado pessoal. */
    public Exportacao exportar(Ator ator, UUID usuario, Origem origem) {
        Exportacao exportacao = transacao.execute(status -> montar(ator.tenant(), usuario));
        auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "exportar_dados_pessoais", "usuario", usuario, null);
        return exportacao;
    }

    /**
     * @param confirmacao o e-mail do usuário, digitado de novo por quem anonimiza: a operação não
     *                    tem volta
     */
    public void anonimizar(Ator ator, UUID usuario, String confirmacao, Origem origem) {
        transacao.executeWithoutResult(status -> {
            Situacao atual = jdbc.query("""
                    SELECT email::text AS email, ativo, anonimizado_em IS NOT NULL AS anonimizado
                      FROM identity.usuarios WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL FOR UPDATE
                    """, (rs, linha) -> new Situacao(rs.getString("email"), rs.getBoolean("ativo"),
                            rs.getBoolean("anonimizado")), usuario, ator.tenant()).stream().findFirst()
                    .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
            if (usuario.equals(ator.id())) {
                throw ErroDeNegocio.regra("id", "AUTOPROTECAO", "Você não pode anonimizar a si mesmo.");
            }
            if (atual.anonimizado()) {
                throw ErroDeNegocio.regra("id", "JA_ANONIMIZADO", "Este usuário já foi anonimizado.");
            }
            if (atual.ativo()) {
                throw ErroDeNegocio.regra("id", "USUARIO_ATIVO", "Desative o usuário antes de anonimizar.");
            }
            // Permissão antes da confirmação: quem não pode anonimizar nem descobre se acertou o e-mail
            regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), usuario), "id",
                    "Este usuário tem permissões que você não tem.");
            String email = atual.email();
            if (confirmacao == null || !confirmacao.strip().equalsIgnoreCase(email)) {
                throw ErroDeNegocio.invalido("confirmacao", "CONFIRMACAO_INCORRETA",
                        "Digite o e-mail do usuário para confirmar.");
            }

            jdbc.update("""
                    UPDATE identity.usuarios
                       SET nome = ?, email = ?::citext, telefone = NULL, senha_hash = NULL,
                           mfa_ativo = false, mfa_secret = NULL, mfa_secret_pendente = NULL, mfa_ativado_em = NULL,
                           mfa_ultimo_passo = NULL, preferencia_tema = 'sistema', ultimo_login_em = NULL,
                           anonimizado_em = now(), updated_at = now(), updated_by = ?
                     WHERE id = ?
                    """, NOME_ANONIMIZADO, "anonimizado-" + usuario + "@invalido", ator.id(), usuario);
            refreshTokens.revogarTodos(usuario);
            links.encerrarTodos(usuario);
            jdbc.update("DELETE FROM identity.codigos_recuperacao WHERE usuario_id = ?", usuario);
            jdbc.update("DELETE FROM identity.desafios_segundo_fator WHERE usuario_id = ?", usuario);
            jdbc.update("DELETE FROM identity.notificacoes WHERE usuario_id = ?", usuario);
            jdbc.update("DELETE FROM identity.usuario_perfis WHERE usuario_id = ?", usuario);
            jdbc.update("DELETE FROM identity.usuario_equipes WHERE usuario_id = ?", usuario);
            jdbc.update("DELETE FROM identity.tentativas_login WHERE email = ?::citext", email);
            // Sem nome nem e-mail no registro: o valor novo não pode repor o que acabou de sair
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "anonimizar", "usuario", usuario,
                    null, Map.of("anonimizado", true));
        });
        eventos.publicar(PublicadorDeEventos.USUARIO_ANONIMIZADO, ator.tenant(), ator.id(),
                new DadosUsuarioAnonimizado(usuario));
    }

    private Exportacao montar(UUID tenant, UUID usuario) {
        Cadastro cadastro = jdbc.query("""
                SELECT id, nome, email::text AS email, telefone, preferencia_tema, ativo, mfa_ativo, created_at,
                       ultimo_login_em, anonimizado_em
                  FROM identity.usuarios WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
                """, (rs, linha) -> new Cadastro(rs.getObject("id", UUID.class), rs.getString("nome"),
                        rs.getString("email"), rs.getString("telefone"), rs.getString("preferencia_tema"),
                        rs.getBoolean("ativo"), rs.getBoolean("mfa_ativo"), instante(rs.getObject("created_at", OffsetDateTime.class)),
                        instante(rs.getObject("ultimo_login_em", OffsetDateTime.class)),
                        instante(rs.getObject("anonimizado_em", OffsetDateTime.class))),
                usuario, tenant).stream().findFirst()
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));

        List<String> perfis = jdbc.queryForList("""
                SELECT p.nome FROM identity.usuario_perfis up JOIN identity.perfis p ON p.id = up.perfil_id
                 WHERE up.usuario_id = ? AND p.tenant_id = ? AND p.deleted_at IS NULL ORDER BY p.nome
                """, String.class, usuario, tenant);
        List<String> equipes = jdbc.queryForList("""
                SELECT e.nome FROM identity.usuario_equipes ue JOIN identity.equipes e ON e.id = ue.equipe_id
                 WHERE ue.usuario_id = ? AND e.tenant_id = ? AND e.deleted_at IS NULL ORDER BY lower(e.nome)
                """, String.class, usuario, tenant);
        // Uma linha por sessão (a rotação grava uma linha por renovação): a mais recente de cada uma
        List<Sessao> sessoes = jdbc.query("""
                SELECT DISTINCT ON (sessao_id) sessao_id, host(ip) AS ip, user_agent, iniciada_em, expira_em, revogado_em
                  FROM identity.refresh_tokens WHERE usuario_id = ?
                 ORDER BY sessao_id, created_at DESC
                """, (rs, linha) -> new Sessao(rs.getObject("sessao_id", UUID.class), rs.getString("ip"),
                        rs.getString("user_agent"), instante(rs.getObject("iniciada_em", OffsetDateTime.class)),
                        instante(rs.getObject("expira_em", OffsetDateTime.class)),
                        instante(rs.getObject("revogado_em", OffsetDateTime.class))), usuario);
        List<TentativaDeLogin> tentativas = jdbc.query("""
                SELECT created_at, host(ip) AS ip, sucesso FROM identity.tentativas_login
                 WHERE email = ?::citext ORDER BY created_at DESC
                """, (rs, linha) -> new TentativaDeLogin(instante(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getString("ip"), rs.getBoolean("sucesso")), cadastro.email());
        List<Notificacao> notificacoes = jdbc.query("""
                SELECT created_at, categoria, titulo, texto, lida_em FROM identity.notificacoes
                 WHERE usuario_id = ? AND tenant_id = ? ORDER BY created_at DESC
                """, (rs, linha) -> new Notificacao(instante(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getString("categoria"), rs.getString("titulo"), rs.getString("texto"),
                        instante(rs.getObject("lida_em", OffsetDateTime.class))), usuario, tenant);
        List<AcaoRegistrada> acoes = jdbc.query("""
                SELECT created_at, acao, entidade, entidade_id, host(ip) AS ip FROM identity.audit_logs
                 WHERE usuario_id = ? AND (tenant_id = ? OR tenant_id IS NULL) ORDER BY created_at DESC
                """, (rs, linha) -> new AcaoRegistrada(instante(rs.getObject("created_at", OffsetDateTime.class)),
                        rs.getString("acao"), rs.getString("entidade"), rs.getObject("entidade_id", UUID.class),
                        rs.getString("ip")), usuario, tenant);
        return new Exportacao(Instant.now(), cadastro, perfis, equipes, sessoes, tentativas, notificacoes, acoes);
    }

    private static Instant instante(OffsetDateTime data) {
        return data == null ? null : data.toInstant();
    }
}
