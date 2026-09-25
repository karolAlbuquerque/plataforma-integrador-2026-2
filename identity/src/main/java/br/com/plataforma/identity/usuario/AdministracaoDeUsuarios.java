package br.com.plataforma.identity.usuario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
import br.com.plataforma.identity.senha.EnvioDeLinks;
import br.com.plataforma.identity.senha.LinksDeSenha;
import br.com.plataforma.identity.senha.LinksDeSenha.LinkEmitido;
import br.com.plataforma.identity.senha.LinksDeSenha.Tipo;
import br.com.plataforma.identity.segundofator.AvisosDeSegundoFator;
import br.com.plataforma.identity.segundofator.SegundoFator;
import br.com.plataforma.identity.seguranca.Ator;
import br.com.plataforma.identity.usuario.UsuarioConsultas.UsuarioDetalhe;

/**
 * Cadastro, edição, desativação e convite de usuários (Requisitos RF13 a RF16 e RF23).
 *
 * O usuário nasce sem senha e define a própria pelo convite: nenhum administrador conhece a senha
 * de ninguém (RF14). Mudança de perfil vale na próxima renovação do token (RF15), porque a
 * renovação relê os acessos do banco. O e-mail do convite sai depois do commit: se o SMTP falhar,
 * o cadastro vale e o convite pode ser reenviado.
 */
@Service
public class AdministracaoDeUsuarios {

    public record Dados(String nome, String email, String telefone, List<UUID> perfis, List<UUID> equipes) {
    }

    /** conviteEnviado é nulo quando a operação não envolveu convite. */
    public record UsuarioSalvo(UsuarioDetalhe usuario, Boolean conviteEnviado) {
    }

    private record Nomeado(UUID id, String nome) {
    }

    private record Atual(UUID id, String nome, String email, String telefone, boolean ativo, boolean senhaDefinida,
                         boolean anonimizado) {
    }

    /** O que sai da transação para o envio do convite, que acontece depois do commit. */
    private record Convite(String nome, String email, LinkEmitido link) {
    }

    private final JdbcTemplate jdbc;
    private final UsuarioConsultas consultas;
    private final RegrasDeAcesso regras;
    private final LinksDeSenha links;
    private final EnvioDeLinks envio;
    private final RefreshTokens refreshTokens;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final SegundoFator segundoFator;
    private final AvisosDeSegundoFator avisos;
    private final Duration validadeDoConvite;

    public AdministracaoDeUsuarios(JdbcTemplate jdbc, UsuarioConsultas consultas, RegrasDeAcesso regras,
                                   LinksDeSenha links, EnvioDeLinks envio, RefreshTokens refreshTokens,
                                   Auditoria auditoria, TransactionTemplate transacao, SegundoFator segundoFator,
                                   AvisosDeSegundoFator avisos,
                                   @Value("${identity.senha.validade-convite}") Duration validadeDoConvite) {
        this.jdbc = jdbc;
        this.consultas = consultas;
        this.regras = regras;
        this.links = links;
        this.envio = envio;
        this.refreshTokens = refreshTokens;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.segundoFator = segundoFator;
        this.avisos = avisos;
        this.validadeDoConvite = validadeDoConvite;
    }

    public UsuarioSalvo cadastrar(Ator ator, Dados entrada, Origem origem) {
        Dados dados = normalizar(entrada);
        UUID id = UUID.randomUUID();
        Convite convite = transacao.execute(status -> {
            List<Nomeado> perfis = perfisDoTenant(ator.tenant(), dados.perfis());
            List<Nomeado> equipes = equipesDoTenant(ator.tenant(), dados.equipes());
            regras.exigirQueTenha(ator, regras.permissoesDosPerfis(ator.tenant(), dados.perfis()), "perfis",
                    "Um dos perfis concede permissões que você não tem.");
            exigirEmailLivre(dados.email(), null);
            try {
                jdbc.update("""
                        INSERT INTO identity.usuarios (id, tenant_id, nome, email, telefone, created_by, updated_by)
                        VALUES (?, ?, ?, ?::citext, ?, ?, ?)
                        """, id, ator.tenant(), dados.nome(), dados.email(), dados.telefone(), ator.id(), ator.id());
            } catch (DuplicateKeyException e) {
                throw emailEmUso();
            }
            perfis.forEach(perfil -> jdbc.update(
                    "INSERT INTO identity.usuario_perfis (usuario_id, perfil_id) VALUES (?, ?)", id, perfil.id()));
            equipes.forEach(equipe -> jdbc.update(
                    "INSERT INTO identity.usuario_equipes (usuario_id, equipe_id) VALUES (?, ?)", id, equipe.id()));

            String ip = origem.ip();
            auditoria.registrar(ator.tenant(), ator.id(), ip, "criar", "usuario", id, null,
                    comNulos("nome", dados.nome(), "email", dados.email(), "telefone", dados.telefone()));
            auditoria.registrar(ator.tenant(), ator.id(), ip, "alterar", "usuario_perfis", id, null,
                    Map.of("perfis", nomes(perfis), "adicionados", nomes(perfis), "removidos", List.of()));
            if (!equipes.isEmpty()) {
                auditoria.registrar(ator.tenant(), ator.id(), ip, "alterar", "usuario_equipes", id, null,
                        Map.of("equipes", nomes(equipes)));
            }
            return emitirConvite(ator, id, dados.nome(), dados.email(), ip);
        });
        return new UsuarioSalvo(detalhe(ator, id), enviar(ator, convite));
    }

    public UsuarioSalvo editar(Ator ator, UUID id, Dados entrada, Origem origem) {
        Dados dados = normalizar(entrada);
        Convite convite = transacao.execute(status -> {
            Atual atual = carregar(ator.tenant(), id);
            boolean euMesmo = id.equals(ator.id());
            if (!euMesmo) {
                regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), id), "id",
                        "Este usuário tem permissões que você não tem.");
            }
            List<Nomeado> perfisAntes = perfisDoUsuario(ator.tenant(), id);
            List<Nomeado> perfis = perfisDoTenant(ator.tenant(), dados.perfis());
            List<Nomeado> equipes = equipesDoTenant(ator.tenant(), dados.equipes());
            Set<UUID> idsAntes = ids(perfisAntes);
            Set<UUID> idsDepois = ids(perfis);
            boolean perfisMudaram = !idsAntes.equals(idsDepois);
            if (euMesmo && perfisMudaram) {
                throw ErroDeNegocio.regra("perfis", "AUTOPROTECAO",
                        "Você não pode alterar os próprios perfis. Peça a outro administrador.");
            }
            Set<UUID> adicionados = new HashSet<>(idsDepois);
            adicionados.removeAll(idsAntes);
            regras.exigirQueTenha(ator, regras.permissoesDosPerfis(ator.tenant(), adicionados), "perfis",
                    "Um dos perfis concede permissões que você não tem.");
            boolean emailMudou = !atual.email().equalsIgnoreCase(dados.email());
            if (emailMudou) {
                exigirEmailLivre(dados.email(), id);
            }

            try {
                jdbc.update("""
                        UPDATE identity.usuarios SET nome = ?, email = ?::citext, telefone = ?, updated_at = now(), updated_by = ?
                         WHERE id = ? AND tenant_id = ?
                        """, dados.nome(), dados.email(), dados.telefone(), ator.id(), id, ator.tenant());
            } catch (DuplicateKeyException e) {
                throw emailEmUso();
            }
            String ip = origem.ip();
            Map<String, Object> anterior = new LinkedHashMap<>();
            Map<String, Object> novo = new LinkedHashMap<>();
            diferenca("nome", atual.nome(), dados.nome(), anterior, novo);
            diferenca("email", atual.email(), dados.email(), anterior, novo);
            diferenca("telefone", atual.telefone(), dados.telefone(), anterior, novo);
            if (!novo.isEmpty()) {
                auditoria.registrar(ator.tenant(), ator.id(), ip, "editar", "usuario", id, anterior, novo);
            }

            if (perfisMudaram) {
                jdbc.update("DELETE FROM identity.usuario_perfis WHERE usuario_id = ?", id);
                perfis.forEach(perfil -> jdbc.update(
                        "INSERT INTO identity.usuario_perfis (usuario_id, perfil_id) VALUES (?, ?)", id, perfil.id()));
                auditoria.registrar(ator.tenant(), ator.id(), ip, "alterar", "usuario_perfis", id,
                        Map.of("perfis", nomes(perfisAntes)),
                        Map.of("perfis", nomes(perfis),
                                "adicionados", nomes(perfis.stream().filter(p -> !idsAntes.contains(p.id())).toList()),
                                "removidos", nomes(perfisAntes.stream().filter(p -> !idsDepois.contains(p.id())).toList())));
                regras.exigirAdministradorRestante(ator.tenant(), "perfis");
            }
            trocarEquipes(ator, id, equipes, ip);

            if (emailMudou && atual.ativo() && !atual.senhaDefinida()) {
                return emitirConvite(ator, id, dados.nome(), dados.email(), ip);
            }
            return null;
        });
        return new UsuarioSalvo(detalhe(ator, id), enviar(ator, convite));
    }

    /** Bloqueia o login na hora, encerra as sessões e cancela o convite (RF16). Nada é apagado. */
    public UsuarioSalvo desativar(Ator ator, UUID id, Origem origem) {
        transacao.executeWithoutResult(status -> {
            Atual atual = carregar(ator.tenant(), id);
            if (id.equals(ator.id())) {
                throw ErroDeNegocio.regra("id", "AUTOPROTECAO", "Você não pode desativar a si mesmo.");
            }
            regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), id), "id",
                    "Este usuário tem permissões que você não tem.");
            if (!atual.ativo()) {
                return;
            }
            jdbc.update("UPDATE identity.usuarios SET ativo = false, updated_at = now(), updated_by = ? WHERE id = ?",
                    ator.id(), id);
            int sessoes = refreshTokens.revogarTodos(id);
            links.encerrarTodos(id);
            regras.exigirAdministradorRestante(ator.tenant(), "id");
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "desativar", "usuario", id,
                    Map.of("ativo", true), Map.of("ativo", false, "sessoesEncerradas", sessoes));
        });
        return new UsuarioSalvo(detalhe(ator, id), null);
    }

    /** Quem nunca definiu senha volta com um convite novo. */
    public UsuarioSalvo reativar(Ator ator, UUID id, Origem origem) {
        Convite convite = transacao.execute(status -> {
            Atual atual = carregar(ator.tenant(), id);
            regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), id), "id",
                    "Este usuário tem permissões que você não tem.");
            if (atual.ativo()) {
                return null;
            }
            jdbc.update("UPDATE identity.usuarios SET ativo = true, updated_at = now(), updated_by = ? WHERE id = ?",
                    ator.id(), id);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "reativar", "usuario", id,
                    Map.of("ativo", false), Map.of("ativo", true));
            return atual.senhaDefinida() ? null : emitirConvite(ator, id, atual.nome(), atual.email(), origem.ip());
        });
        return new UsuarioSalvo(detalhe(ator, id), enviar(ator, convite));
    }

    /** Emite um convite novo, que cancela o anterior (RF14). */
    public UsuarioSalvo reenviarConvite(Ator ator, UUID id, Origem origem) {
        Convite convite = transacao.execute(status -> {
            Atual atual = carregar(ator.tenant(), id);
            regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), id), "id",
                    "Este usuário tem permissões que você não tem.");
            if (!atual.ativo()) {
                throw ErroDeNegocio.regra("id", "USUARIO_INATIVO", "Reative o usuário antes de reenviar o convite.");
            }
            if (atual.senhaDefinida()) {
                throw ErroDeNegocio.regra("id", "CONVITE_DESNECESSARIO",
                        "Este usuário já definiu a senha. Se esqueceu, ele pode pedir a recuperação na tela de entrada.");
            }
            return emitirConvite(ator, id, atual.nome(), atual.email(), origem.ip());
        });
        return new UsuarioSalvo(detalhe(ator, id), enviar(ator, convite));
    }

    /**
     * Para quem perdeu o celular e os códigos de recuperação (Requisito RF10): apaga o segundo fator,
     * encerra as sessões e avisa por e-mail. No próximo login, o cadastro do aplicativo é pedido de novo.
     */
    public UsuarioSalvo redefinirSegundoFator(Ator ator, UUID id, Origem origem) {
        Atual atual = transacao.execute(status -> {
            Atual carregado = carregar(ator.tenant(), id);
            regras.exigirQueTenha(ator, regras.permissoesDoUsuario(ator.tenant(), id), "id",
                    "Este usuário tem permissões que você não tem.");
            segundoFator.redefinir(id, ator.id());
            int sessoes = refreshTokens.revogarTodos(id);
            auditoria.registrar(ator.tenant(), ator.id(), origem.ip(), "redefinir", "segundo_fator", id,
                    null, Map.of("sessoesEncerradas", sessoes));
            return carregado;
        });
        avisos.redefinido(atual.nome(), atual.email(), ator.tenant());
        return new UsuarioSalvo(detalhe(ator, id), null);
    }

    // ------------------------------------------------------------------ apoio

    private Convite emitirConvite(Ator ator, UUID usuario, String nome, String email, String ip) {
        LinkEmitido link = links.emitir(usuario, Tipo.CONVITE, validadeDoConvite);
        auditoria.registrar(ator.tenant(), ator.id(), ip, "convidar", "usuario", usuario, null,
                Map.of("email", email, "expiraEm", link.expiraEm().toString()));
        return new Convite(nome, email, link);
    }

    private Boolean enviar(Ator ator, Convite convite) {
        return convite == null ? null : envio.enviar(Tipo.CONVITE, convite.nome(), convite.email(), ator.tenant(), convite.link());
    }

    private UsuarioDetalhe detalhe(Ator ator, UUID id) {
        return consultas.detalhe(ator.tenant(), id).orElseThrow(AdministracaoDeUsuarios::naoEncontrado);
    }

    private Atual carregar(UUID tenant, UUID id) {
        Atual atual = jdbc.query("""
                SELECT id, nome, email::text AS email, telefone, ativo, senha_hash IS NOT NULL AS senha_definida,
                       anonimizado_em IS NOT NULL AS anonimizado
                  FROM identity.usuarios WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
                   FOR UPDATE
                """, (rs, linha) -> new Atual(rs.getObject("id", UUID.class), rs.getString("nome"), rs.getString("email"),
                        rs.getString("telefone"), rs.getBoolean("ativo"), rs.getBoolean("senha_definida"),
                        rs.getBoolean("anonimizado")),
                id, tenant).stream().findFirst().orElseThrow(AdministracaoDeUsuarios::naoEncontrado);
        // Anonimizado não volta: reativar ou editar mandaria convite para um e-mail que não existe
        if (atual.anonimizado()) {
            throw ErroDeNegocio.regra("id", "USUARIO_ANONIMIZADO", "Este usuário foi anonimizado e não pode ser alterado.");
        }
        return atual;
    }

    /** Quem já era membro continua com a marcação de líder; quem entra, entra como membro comum. */
    private void trocarEquipes(Ator ator, UUID usuario, List<Nomeado> equipes, String ip) {
        List<Nomeado> antes = jdbc.query("""
                SELECT e.id, e.nome FROM identity.usuario_equipes ue
                  JOIN identity.equipes e ON e.id = ue.equipe_id AND e.deleted_at IS NULL
                 WHERE ue.usuario_id = ? AND e.tenant_id = ? ORDER BY lower(e.nome)
                """, (rs, linha) -> new Nomeado(rs.getObject("id", UUID.class), rs.getString("nome")), usuario, ator.tenant());
        Set<UUID> idsAntes = ids(antes);
        Set<UUID> idsDepois = ids(equipes);
        if (idsAntes.equals(idsDepois)) {
            return;
        }
        for (UUID saiu : idsAntes) {
            if (!idsDepois.contains(saiu)) {
                jdbc.update("DELETE FROM identity.usuario_equipes WHERE usuario_id = ? AND equipe_id = ?", usuario, saiu);
            }
        }
        for (UUID entrou : idsDepois) {
            if (!idsAntes.contains(entrou)) {
                jdbc.update("INSERT INTO identity.usuario_equipes (usuario_id, equipe_id) VALUES (?, ?)", usuario, entrou);
            }
        }
        auditoria.registrar(ator.tenant(), ator.id(), ip, "alterar", "usuario_equipes", usuario,
                Map.of("equipes", nomes(antes)), Map.of("equipes", nomes(equipes)));
    }

    /** Perfil de outro tenant responde como inexistente: não revela que ele existe em outra empresa. */
    private List<Nomeado> perfisDoTenant(UUID tenant, List<UUID> ids) {
        List<Nomeado> achados = ids.isEmpty() ? List.of() : jdbc.query("""
                SELECT id, nome FROM identity.perfis
                 WHERE tenant_id = ? AND deleted_at IS NULL AND id = ANY(?::uuid[])
                 ORDER BY sistema DESC, lower(nome)
                """, (rs, linha) -> new Nomeado(rs.getObject("id", UUID.class), rs.getString("nome")),
                tenant, UsuarioConsultas.comoArray(ids));
        if (achados.size() != ids.size()) {
            throw ErroDeNegocio.regra("perfis", "PERFIL_INEXISTENTE", "Um dos perfis escolhidos não existe mais.");
        }
        return achados;
    }

    private List<Nomeado> equipesDoTenant(UUID tenant, List<UUID> ids) {
        List<Nomeado> achadas = ids.isEmpty() ? List.of() : jdbc.query("""
                SELECT id, nome FROM identity.equipes
                 WHERE tenant_id = ? AND deleted_at IS NULL AND id = ANY(?::uuid[])
                 ORDER BY lower(nome)
                """, (rs, linha) -> new Nomeado(rs.getObject("id", UUID.class), rs.getString("nome")),
                tenant, UsuarioConsultas.comoArray(ids));
        if (achadas.size() != ids.size()) {
            throw ErroDeNegocio.regra("equipes", "EQUIPE_INEXISTENTE", "Uma das equipes escolhidas não existe mais.");
        }
        return achadas;
    }

    private List<Nomeado> perfisDoUsuario(UUID tenant, UUID usuario) {
        return jdbc.query("""
                SELECT p.id, p.nome FROM identity.usuario_perfis up
                  JOIN identity.perfis p ON p.id = up.perfil_id AND p.tenant_id = ? AND p.deleted_at IS NULL
                 WHERE up.usuario_id = ? ORDER BY p.sistema DESC, lower(p.nome)
                """, (rs, linha) -> new Nomeado(rs.getObject("id", UUID.class), rs.getString("nome")), tenant, usuario);
    }

    /** E-mail único global entre usuários não excluídos (decisão D8) — inclusive de outro tenant. */
    private void exigirEmailLivre(String email, UUID exceto) {
        Boolean usado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity.usuarios
                                WHERE email = ?::citext AND deleted_at IS NULL AND id IS DISTINCT FROM ?)
                """, Boolean.class, email, exceto);
        if (Boolean.TRUE.equals(usado)) {
            throw emailEmUso();
        }
    }

    private static Dados normalizar(Dados dados) {
        String telefone = dados.telefone() == null || dados.telefone().isBlank() ? null : dados.telefone().strip();
        List<UUID> perfis = new ArrayList<>(new LinkedHashSet<>(Objects.requireNonNullElse(dados.perfis(), List.of())));
        List<UUID> equipes = new ArrayList<>(new LinkedHashSet<>(Objects.requireNonNullElse(dados.equipes(), List.of())));
        return new Dados(dados.nome().strip(), dados.email().strip().toLowerCase(Locale.ROOT), telefone, perfis, equipes);
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

    private static Set<UUID> ids(List<Nomeado> nomeados) {
        Set<UUID> ids = new HashSet<>();
        nomeados.forEach(nomeado -> ids.add(nomeado.id()));
        return ids;
    }

    private static List<String> nomes(List<Nomeado> nomeados) {
        return nomeados.stream().map(Nomeado::nome).toList();
    }

    private static ErroDeNegocio emailEmUso() {
        return ErroDeNegocio.conflito("email", "EMAIL_EM_USO", "Este e-mail já está em uso.");
    }

    private static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Usuário não encontrado.");
    }
}
