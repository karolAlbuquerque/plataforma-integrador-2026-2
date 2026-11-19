package br.com.plataforma.identity.tenant;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.acesso.RegrasDeAcesso;
import br.com.plataforma.identity.api.ErroDeNegocio;
import br.com.plataforma.identity.api.NaoEncontradoException;
import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.autenticacao.RefreshTokens;
import br.com.plataforma.identity.inicializacao.PerfisIniciais;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.senha.EnvioDeLinks;
import br.com.plataforma.identity.senha.LinksDeSenha;
import br.com.plataforma.identity.senha.LinksDeSenha.LinkEmitido;
import br.com.plataforma.identity.senha.LinksDeSenha.Tipo;
import br.com.plataforma.identity.tenant.Tenants.TenantDetalhe;
import br.com.plataforma.identity.usuario.UsuarioConsultas;
import br.com.plataforma.identity.usuario.UsuarioConsultas.Filtro;
import br.com.plataforma.identity.usuario.UsuarioConsultas.PerfilDoUsuario;
import br.com.plataforma.identity.usuario.UsuarioConsultas.UsuarioDaLista;
import br.com.plataforma.identity.usuario.UsuarioRepositorio;

/**
 * Cadastro, edição, ativação/desativação de empresas contratantes e gestão dos usuários de cada
 * tenant diretamente pela tela de empresas (Requisito RF27).
 */
@Service
public class AdministracaoDeTenants {

    private static final Pattern CNPJ_DIGITOS = Pattern.compile("^[0-9]{14}$");
    private static final Pattern SUBDOMINIO = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern EMAIL_SIMPLES = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record DadosDeCriacao(String razaoSocial, String nomeFantasia, String cnpj, String subdominio,
                                 String adminNome, String adminEmail) {
    }

    public record DadosDeEdicao(String razaoSocial, String nomeFantasia, String cnpj, String subdominio) {
    }

    public record TenantSalvo(TenantDetalhe tenant, UUID administradorId, Boolean conviteEnviado) {
    }

    public record UsuariosDoTenant(List<UsuarioDaLista> usuarios, List<PerfilDoUsuario> perfisDisponiveis) {
    }

    public record CandidatoDoTenant(UUID id, String nome, String email, String empresaOrigem) {
    }

    public record NovoUsuarioDoTenant(String nome, String email, String telefone, UUID perfilId) {
    }

    private record Atual(UUID id, String razaoSocial, String nomeFantasia, String cnpj, String subdominio, boolean ativo) {
    }

    private record Convite(UUID tenantId, UUID usuarioId, String nome, String email, LinkEmitido link) {
    }

    private final JdbcTemplate jdbc;
    private final Tenants tenants;
    private final PerfisIniciais perfisIniciais;
    private final UsuarioConsultas usuarioConsultas;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RegrasDeAcesso regras;
    private final LinksDeSenha links;
    private final EnvioDeLinks envio;
    private final RefreshTokens refreshTokens;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Duration validadeDoConvite;

    public AdministracaoDeTenants(JdbcTemplate jdbc, Tenants tenants, PerfisIniciais perfisIniciais,
                                  UsuarioConsultas usuarioConsultas, UsuarioRepositorio usuarioRepositorio,
                                  RegrasDeAcesso regras, LinksDeSenha links, EnvioDeLinks envio,
                                  RefreshTokens refreshTokens, Auditoria auditoria, TransactionTemplate transacao,
                                  @Value("${identity.senha.validade-convite}") Duration validadeDoConvite) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.perfisIniciais = perfisIniciais;
        this.usuarioConsultas = usuarioConsultas;
        this.usuarioRepositorio = usuarioRepositorio;
        this.regras = regras;
        this.links = links;
        this.envio = envio;
        this.refreshTokens = refreshTokens;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.validadeDoConvite = validadeDoConvite;
    }

    /** Garante que o administrador logado esteja vinculado como ADMINISTRADOR no tenant ativo da sessão. */
    public void garantirVinculoNoTenantAtivo(Ator ator) {
        usuarioRepositorio.vincularComoAdministradorSeSemPerfil(ator.id(), ator.tenant());
    }

    public TenantSalvo cadastrar(Ator ator, DadosDeCriacao entrada, Origem origem) {
        DadosDeEdicao dados = validarDados(entrada.razaoSocial(), entrada.nomeFantasia(), entrada.cnpj(), entrada.subdominio());
        String adminEmail = limpar(entrada.adminEmail());
        String adminNome = limpar(entrada.adminNome());
        if (adminEmail != null) {
            adminEmail = adminEmail.toLowerCase(Locale.ROOT);
            if (!EMAIL_SIMPLES.matcher(adminEmail).matches()) {
                throw ErroDeNegocio.invalido("adminEmail", "CAMPO_INVALIDO", "Informe um e-mail válido para o administrador.");
            }
            if (adminNome == null) {
                throw ErroDeNegocio.invalido("adminNome", "CAMPO_OBRIGATORIO", "Informe o nome do administrador inicial.");
            }
        }

        UUID id = UUID.randomUUID();
        String emailFinal = adminEmail;
        String nomeFinal = adminNome;

        Convite convite = transacao.execute(status -> {
            exigirCnpjLivre(dados.cnpj(), null);
            exigirSubdominioLivre(dados.subdominio(), null);
            if (emailFinal != null) {
                exigirEmailLivre(emailFinal);
            }

            try {
                jdbc.update("""
                        INSERT INTO identity.tenants (id, razao_social, nome_fantasia, cnpj, subdominio, ativo)
                        VALUES (?, ?, ?, ?, ?, true)
                        """, id, dados.razaoSocial(), dados.nomeFantasia(), dados.cnpj(), dados.subdominio());
            } catch (DuplicateKeyException e) {
                revalidarConflito(dados.cnpj(), dados.subdominio(), null);
                throw e;
            }

            perfisIniciais.garantirPerfis(id);
            // Quem criou a empresa já nasce vinculado a ela como ADMINISTRADOR
            usuarioRepositorio.vincularComoAdministradorSeSemPerfil(ator.id(), id);

            Map<String, Object> novo = comNulos(
                    "razaoSocial", dados.razaoSocial(),
                    "nomeFantasia", dados.nomeFantasia(),
                    "cnpj", dados.cnpj(),
                    "subdominio", dados.subdominio());
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "criar", "tenant", id, null, novo);
            if (!id.equals(ator.tenant())) {
                auditoria.registrar(id, ator.id(), origem.ip(), "criar", "tenant", id, null, novo);
            }

            if (emailFinal == null) {
                return null;
            }

            UUID usuarioId = UUID.randomUUID();
            try {
                jdbc.update("""
                        INSERT INTO identity.usuarios (id, tenant_id, nome, email, created_by, updated_by)
                        VALUES (?, ?, ?, ?::citext, ?, ?)
                        """, usuarioId, id, nomeFinal, emailFinal, ator.id(), ator.id());
            } catch (DuplicateKeyException e) {
                throw ErroDeNegocio.conflito("adminEmail", "EMAIL_EM_USO", "Este e-mail já está em uso.");
            }
            jdbc.update("""
                    INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                    SELECT ?, p.id FROM identity.perfis p
                     WHERE p.tenant_id = ? AND p.nome = 'ADMINISTRADOR' AND p.sistema AND p.deleted_at IS NULL
                    """, usuarioId, id);
            LinkEmitido link = links.emitir(usuarioId, Tipo.CONVITE, validadeDoConvite);
            auditoria.registrar(id, ator.id(), origem.ip(), "criar", "usuario", usuarioId, null,
                    Map.of("nome", nomeFinal, "email", emailFinal, "perfil", "ADMINISTRADOR"));
            auditoria.registrar(id, ator.id(), origem.ip(), "convidar", "usuario", usuarioId, null,
                    Map.of("email", emailFinal, "expiraEm", link.expiraEm().toString()));
            return new Convite(id, usuarioId, nomeFinal, emailFinal, link);
        });

        Boolean enviado = convite == null ? null
                : envio.enviar(Tipo.CONVITE, convite.nome(), convite.email(), convite.tenantId(), convite.link());
        return new TenantSalvo(detalhe(id, ator.tenant()), convite == null ? null : convite.usuarioId(), enviado);
    }

    public TenantDetalhe editar(Ator ator, UUID id, DadosDeEdicao entrada, Origem origem) {
        DadosDeEdicao dados = validarDados(entrada.razaoSocial(), entrada.nomeFantasia(), entrada.cnpj(), entrada.subdominio());
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(id);
            if (!Objects.equals(atual.cnpj(), dados.cnpj())) {
                exigirCnpjLivre(dados.cnpj(), id);
            }
            if (!Objects.equals(atual.subdominio(), dados.subdominio())) {
                exigirSubdominioLivre(dados.subdominio(), id);
            }

            try {
                jdbc.update("""
                        UPDATE identity.tenants
                           SET razao_social = ?, nome_fantasia = ?, cnpj = ?, subdominio = ?, updated_at = now()
                         WHERE id = ? AND deleted_at IS NULL
                        """, dados.razaoSocial(), dados.nomeFantasia(), dados.cnpj(), dados.subdominio(), id);
            } catch (DuplicateKeyException e) {
                revalidarConflito(dados.cnpj(), dados.subdominio(), id);
                throw e;
            }

            Map<String, Object> anterior = new LinkedHashMap<>();
            Map<String, Object> novo = new LinkedHashMap<>();
            diferenca("razaoSocial", atual.razaoSocial(), dados.razaoSocial(), anterior, novo);
            diferenca("nomeFantasia", atual.nomeFantasia(), dados.nomeFantasia(), anterior, novo);
            diferenca("cnpj", atual.cnpj(), dados.cnpj(), anterior, novo);
            diferenca("subdominio", atual.subdominio(), dados.subdominio(), anterior, novo);
            if (!novo.isEmpty()) {
                auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "editar", "tenant", id, anterior, novo);
                if (!id.equals(ator.tenant())) {
                    auditoria.registrar(id, ator.id(), origem.ip(), "editar", "tenant", id, anterior, novo);
                }
            }
        });
        return detalhe(id, ator.tenant());
    }

    public TenantDetalhe desativar(Ator ator, UUID id, Origem origem) {
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(id);
            UUID tenantDeOrigem = tenantDeOrigemDoUsuario(ator.id());
            if (id.equals(ator.tenant()) || id.equals(tenantDeOrigem)) {
                throw ErroDeNegocio.regra("id", "AUTOPROTECAO",
                        "Você não pode desativar a empresa em que está conectado nem a sua empresa de origem.");
            }
            if (!atual.ativo()) {
                return;
            }
            jdbc.update("UPDATE identity.tenants SET ativo = false, updated_at = now() WHERE id = ?", id);
            int sessoes = refreshTokens.revogarDoTenant(id);
            Map<String, Object> anterior = Map.of("ativo", true);
            Map<String, Object> novo = Map.of("ativo", false, "sessoesEncerradas", sessoes);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "desativar", "tenant", id, anterior, novo);
            auditoria.registrar(id, ator.id(), origem.ip(), "desativar", "tenant", id, anterior, novo);
        });
        return detalhe(id, ator.tenant());
    }

    public TenantDetalhe reativar(Ator ator, UUID id, Origem origem) {
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(id);
            if (atual.ativo()) {
                return;
            }
            jdbc.update("UPDATE identity.tenants SET ativo = true, updated_at = now() WHERE id = ?", id);
            perfisIniciais.garantirPerfis(id);
            Map<String, Object> anterior = Map.of("ativo", false);
            Map<String, Object> novo = Map.of("ativo", true);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "reativar", "tenant", id, anterior, novo);
            if (!id.equals(ator.tenant())) {
                auditoria.registrar(id, ator.id(), origem.ip(), "reativar", "tenant", id, anterior, novo);
            }
        });
        return detalhe(id, ator.tenant());
    }

    // ------------------------------------------------------------------ usuários do tenant

    public UsuariosDoTenant listarUsuarios(UUID tenantId) {
        if (!tenants.existe(tenantId)) {
            throw naoEncontrado();
        }
        List<UsuarioDaLista> usuarios = usuarioConsultas
                .listar(tenantId, new Filtro(null, null, null), 0, 100, "nome,asc")
                .itens();
        List<PerfilDoUsuario> perfis = jdbc.query("""
                SELECT id, nome, sistema
                  FROM identity.perfis
                 WHERE tenant_id = ? AND deleted_at IS NULL
                 ORDER BY sistema DESC, lower(nome)
                """, (rs, linha) -> new PerfilDoUsuario(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        UsuarioConsultas.rotulo(rs.getString("nome"), rs.getBoolean("sistema")),
                        rs.getBoolean("sistema")),
                tenantId);
        return new UsuariosDoTenant(usuarios, perfis);
    }

    /** Busca usuários ativos de outras empresas que ainda não estão vinculados a este tenant. */
    public List<CandidatoDoTenant> buscarCandidatos(UUID tenantId, String busca) {
        if (!tenants.existe(tenantId)) {
            throw naoEncontrado();
        }
        String trecho = (busca == null || busca.isBlank()) ? "%" : "%" + escaparLike(busca.strip()) + "%";
        return jdbc.query("""
                SELECT u.id, u.nome, u.email::text AS email, coalesce(t.nome_fantasia, t.razao_social) AS empresa_origem
                  FROM identity.usuarios u
                  JOIN identity.tenants t ON t.id = u.tenant_id
                 WHERE u.deleted_at IS NULL
                   AND u.ativo
                   AND u.anonimizado_em IS NULL
                   AND u.tenant_id <> ?
                   AND NOT EXISTS (
                       SELECT 1 FROM identity.usuario_perfis up
                         JOIN identity.perfis p ON p.id = up.perfil_id
                        WHERE up.usuario_id = u.id AND p.tenant_id = ? AND p.deleted_at IS NULL
                   )
                   AND (u.nome ILIKE ? ESCAPE '\\' OR u.email::text ILIKE ? ESCAPE '\\')
                 ORDER BY lower(u.nome)
                 LIMIT 10
                """, (rs, linha) -> new CandidatoDoTenant(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("email"),
                        rs.getString("empresa_origem")),
                tenantId, tenantId, trecho, trecho);
    }

    /** Vincula um usuário já existente na plataforma a um perfil deste tenant. */
    public UsuariosDoTenant vincularUsuarioExistente(Ator ator, UUID tenantId, UUID usuarioId, UUID perfilId, Origem origem) {
        transacao.executeWithoutResult(status -> {
            carregar(tenantId);
            Boolean usuarioExiste = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM identity.usuarios
                                    WHERE id = ? AND ativo AND anonimizado_em IS NULL AND deleted_at IS NULL)
                    """, Boolean.class, usuarioId);
            if (!Boolean.TRUE.equals(usuarioExiste)) {
                throw ErroDeNegocio.regra("usuarioId", "USUARIO_INEXISTENTE", "Usuário não encontrado ou inativo.");
            }
            String nomePerfil = nomeDoPerfilNoTenant(tenantId, perfilId);
            regras.exigirQueTenha(ator, regras.permissoesDosPerfis(tenantId, List.of(perfilId)), "perfilId",
                    "Este perfil concede permissões que você não tem.");
            jdbc.update("""
                    INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, usuarioId, perfilId);
            auditoria.registrar(tenantId, ator.id(), origem.ip(), "alterar", "usuario_perfis", usuarioId, null,
                    Map.of("perfis", List.of(nomePerfil), "adicionados", List.of(nomePerfil)));
        });
        return listarUsuarios(tenantId);
    }

    /** Cadastra e convida um usuário inédito diretamente dentro do tenant informado. */
    public UsuariosDoTenant cadastrarNovoUsuarioNoTenant(Ator ator, UUID tenantId, NovoUsuarioDoTenant entrada, Origem origem) {
        String nome = limpar(entrada.nome());
        String email = limpar(entrada.email());
        String telefone = limpar(entrada.telefone());
        if (nome == null) {
            throw ErroDeNegocio.invalido("nome", "CAMPO_OBRIGATORIO", "Informe o nome do usuário.");
        }
        if (email == null || !EMAIL_SIMPLES.matcher(email.toLowerCase(Locale.ROOT)).matches()) {
            throw ErroDeNegocio.invalido("email", "CAMPO_INVALIDO", "Informe um e-mail válido.");
        }
        String emailLimpo = email.toLowerCase(Locale.ROOT);
        UUID usuarioId = UUID.randomUUID();

        Convite convite = transacao.execute(status -> {
            carregar(tenantId);
            String nomePerfil = nomeDoPerfilNoTenant(tenantId, entrada.perfilId());
            regras.exigirQueTenha(ator, regras.permissoesDosPerfis(tenantId, List.of(entrada.perfilId())), "perfilId",
                    "Este perfil concede permissões que você não tem.");
            exigirEmailLivreNoCampo(emailLimpo, "email");
            try {
                jdbc.update("""
                        INSERT INTO identity.usuarios (id, tenant_id, nome, email, telefone, created_by, updated_by)
                        VALUES (?, ?, ?, ?::citext, ?, ?, ?)
                        """, usuarioId, tenantId, nome, emailLimpo, telefone, ator.id(), ator.id());
            } catch (DuplicateKeyException e) {
                throw ErroDeNegocio.conflito("email", "EMAIL_EM_USO", "Este e-mail já está em uso.");
            }
            jdbc.update("INSERT INTO identity.usuario_perfis (usuario_id, perfil_id) VALUES (?, ?)",
                    usuarioId, entrada.perfilId());
            LinkEmitido link = links.emitir(usuarioId, Tipo.CONVITE, validadeDoConvite);
            auditoria.registrar(tenantId, ator.id(), origem.ip(), "criar", "usuario", usuarioId, null,
                    comNulos("nome", nome, "email", emailLimpo, "telefone", telefone));
            auditoria.registrar(tenantId, ator.id(), origem.ip(), "alterar", "usuario_perfis", usuarioId, null,
                    Map.of("perfis", List.of(nomePerfil), "adicionados", List.of(nomePerfil)));
            auditoria.registrar(tenantId, ator.id(), origem.ip(), "convidar", "usuario", usuarioId, null,
                    Map.of("email", emailLimpo, "expiraEm", link.expiraEm().toString()));
            return new Convite(tenantId, usuarioId, nome, emailLimpo, link);
        });

        envio.enviar(Tipo.CONVITE, convite.nome(), convite.email(), convite.tenantId(), convite.link());
        return listarUsuarios(tenantId);
    }

    /** Remove o vínculo de um usuário com o tenant (ou impede se for o próprio usuário ou o último admin). */
    public UsuariosDoTenant removerUsuarioDoTenant(Ator ator, UUID tenantId, UUID usuarioId, Origem origem) {
        transacao.executeWithoutResult(status -> {
            carregar(tenantId);
            if (usuarioId.equals(ator.id())) {
                throw ErroDeNegocio.regra("usuarioId", "AUTOPROTECAO", "Você não pode remover a si mesmo da empresa.");
            }
            UUID tenantOrigem = tenantDeOrigemDoUsuario(usuarioId);
            if (tenantId.equals(tenantOrigem)) {
                throw ErroDeNegocio.regra("usuarioId", "USUARIO_DE_ORIGEM",
                        "Esta é a empresa de origem deste usuário. Para bloquear o acesso dele, desative-o em Administração → Usuários.");
            }
            jdbc.update("""
                    DELETE FROM identity.usuario_perfis up
                     USING identity.perfis p
                     WHERE up.perfil_id = p.id AND up.usuario_id = ? AND p.tenant_id = ?
                    """, usuarioId, tenantId);
            jdbc.update("""
                    DELETE FROM identity.usuario_equipes ue
                     USING identity.equipes e
                     WHERE ue.equipe_id = e.id AND ue.usuario_id = ? AND e.tenant_id = ?
                    """, usuarioId, tenantId);
            regras.exigirAdministradorRestante(tenantId, "usuarioId");
            auditoria.registrar(tenantId, ator.id(), origem.ip(), "excluir", "usuario_perfis", usuarioId, null, null);
        });
        return listarUsuarios(tenantId);
    }

    private String nomeDoPerfilNoTenant(UUID tenantId, UUID perfilId) {
        if (perfilId == null) {
            throw ErroDeNegocio.invalido("perfilId", "CAMPO_OBRIGATORIO", "Escolha um perfil.");
        }
        return jdbc.queryForList("""
                SELECT nome FROM identity.perfis WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
                """, String.class, perfilId, tenantId).stream().findFirst()
                .orElseThrow(() -> ErroDeNegocio.regra("perfilId", "PERFIL_INEXISTENTE", "O perfil escolhido não pertence a esta empresa."));
    }

    private TenantDetalhe detalhe(UUID id, UUID tenantAtual) {
        return tenants.detalhe(id, tenantAtual).orElseThrow(AdministracaoDeTenants::naoEncontrado);
    }

    private Atual carregar(UUID id) {
        return jdbc.query("""
                SELECT id, razao_social, nome_fantasia, cnpj, subdominio, ativo
                  FROM identity.tenants
                 WHERE id = ? AND deleted_at IS NULL
                   FOR UPDATE
                """, (rs, linha) -> new Atual(
                        rs.getObject("id", UUID.class),
                        rs.getString("razao_social"),
                        rs.getString("nome_fantasia"),
                        rs.getString("cnpj"),
                        rs.getString("subdominio"),
                        rs.getBoolean("ativo")),
                id).stream().findFirst().orElseThrow(AdministracaoDeTenants::naoEncontrado);
    }

    private UUID tenantDeOrigemDoUsuario(UUID usuarioId) {
        return jdbc.queryForList("SELECT tenant_id FROM identity.usuarios WHERE id = ? AND deleted_at IS NULL",
                UUID.class, usuarioId).stream().findFirst().orElse(null);
    }

    private void exigirCnpjLivre(String cnpj, UUID exceto) {
        if (cnpj == null) {
            return;
        }
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.tenants
                                WHERE cnpj = ? AND id IS DISTINCT FROM ?)
                """, Boolean.class, cnpj, exceto);
        if (Boolean.TRUE.equals(usado)) {
            throw ErroDeNegocio.conflito("cnpj", "CNPJ_EM_USO", "Este CNPJ já está cadastrado em outra empresa.");
        }
    }

    private void exigirSubdominioLivre(String subdominio, UUID exceto) {
        if (subdominio == null) {
            return;
        }
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.tenants
                                WHERE subdominio = ? AND id IS DISTINCT FROM ?)
                """, Boolean.class, subdominio, exceto);
        if (Boolean.TRUE.equals(usado)) {
            throw ErroDeNegocio.conflito("subdominio", "SUBDOMINIO_EM_USO", "Este subdomínio já está em uso por outra empresa.");
        }
    }

    private void exigirEmailLivre(String email) {
        exigirEmailLivreNoCampo(email, "adminEmail");
    }

    private void exigirEmailLivreNoCampo(String email, String campo) {
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.usuarios WHERE email = ?::citext AND deleted_at IS NULL)
                """, Boolean.class, email);
        if (Boolean.TRUE.equals(usado)) {
            throw ErroDeNegocio.conflito(campo, "EMAIL_EM_USO", "Este e-mail já está em uso.");
        }
    }

    private void revalidarConflito(String cnpj, String subdominio, UUID exceto) {
        exigirCnpjLivre(cnpj, exceto);
        exigirSubdominioLivre(subdominio, exceto);
    }

    private static DadosDeEdicao validarDados(String razaoSocialBruta, String nomeFantasiaBruto, String cnpjBruto,
                                              String subdominioBruto) {
        String razaoSocial = limpar(razaoSocialBruta);
        if (razaoSocial == null) {
            throw ErroDeNegocio.invalido("razaoSocial", "CAMPO_OBRIGATORIO", "Informe a razão social.");
        }
        String nomeFantasia = limpar(nomeFantasiaBruto);
        String cnpj = limpar(cnpjBruto);
        if (cnpj != null) {
            cnpj = cnpj.replaceAll("[./\\-\\s]", "");
            if (!CNPJ_DIGITOS.matcher(cnpj).matches()) {
                throw ErroDeNegocio.invalido("cnpj", "CAMPO_INVALIDO", "O CNPJ deve ter 14 dígitos numéricos.");
            }
        }
        String subdominio = limpar(subdominioBruto);
        if (subdominio != null) {
            subdominio = subdominio.toLowerCase(Locale.ROOT);
            if (subdominio.length() > 63 || !SUBDOMINIO.matcher(subdominio).matches()) {
                throw ErroDeNegocio.invalido("subdominio", "CAMPO_INVALIDO",
                        "Use apenas letras minúsculas, números e hífen (sem começar ou terminar com hífen).");
            }
        }
        return new DadosDeEdicao(razaoSocial, nomeFantasia, cnpj, subdominio);
    }

    private static String limpar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.strip();
        return limpo.isEmpty() ? null : limpo;
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void diferenca(String campo, Object antes, Object depois, Map<String, Object> anterior,
                                  Map<String, Object> novo) {
        if (!Objects.equals(antes, depois)) {
            anterior.put(campo, antes);
            novo.put(campo, depois);
        }
    }

    private static Map<String, Object> comNulos(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    private static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Empresa não encontrada.");
    }
}
