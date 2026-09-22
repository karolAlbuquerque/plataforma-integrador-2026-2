package br.com.plataforma.identity.inicializacao;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.auditoria.Auditoria;
import br.com.plataforma.identity.senha.EnvioDeLinks;
import br.com.plataforma.identity.senha.LinksDeSenha;
import br.com.plataforma.identity.senha.LinksDeSenha.LinkEmitido;
import br.com.plataforma.identity.senha.LinksDeSenha.Tipo;

/**
 * Quinto passo da subida: o primeiro administrador do tenant de produção (aprovado em 21/09/2026).
 * O tenant nasce pela migration V2 sem nenhum usuário, e sem usuário ninguém convida ninguém. Com
 * ADMIN_INICIAL_EMAIL definido e o tenant ainda vazio, cria um ADMINISTRADOR e manda o convite; em
 * qualquer outro caso não faz nada — rodar de novo é seguro.
 */
@Component
@Order(5)
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    static final UUID TENANT_DE_PRODUCAO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final JdbcTemplate jdbc;
    private final LinksDeSenha links;
    private final EnvioDeLinks envio;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final String email;
    private final String nome;
    private final Duration validadeDoConvite;

    public AdministradorInicial(JdbcTemplate jdbc, LinksDeSenha links, EnvioDeLinks envio, Auditoria auditoria,
                                TransactionTemplate transacao,
                                @Value("${identity.admin-inicial.email}") String email,
                                @Value("${identity.admin-inicial.nome}") String nome,
                                @Value("${identity.senha.validade-convite}") Duration validadeDoConvite) {
        this.jdbc = jdbc;
        this.links = links;
        this.envio = envio;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.email = email == null ? "" : email.strip().toLowerCase(java.util.Locale.ROOT);
        this.nome = nome == null || nome.isBlank() ? "Administrador" : nome.strip();
        this.validadeDoConvite = validadeDoConvite;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (email.isEmpty()) {
            return;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            log.error("ADMIN_INICIAL_EMAIL não é um e-mail válido; administrador inicial não criado.");
            return;
        }
        UUID id = UUID.randomUUID();
        LinkEmitido convite = transacao.execute(status -> {
            Integer usuarios = jdbc.queryForObject(
                    "SELECT count(*) FROM identity.usuarios WHERE tenant_id = ? AND deleted_at IS NULL",
                    Integer.class, TENANT_DE_PRODUCAO);
            if (usuarios == null || usuarios > 0) {
                return null;   // já tem gente: quem administra agora é o administrador do tenant
            }
            Boolean emUso = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM identity.usuarios WHERE email = ?::citext AND deleted_at IS NULL)",
                    Boolean.class, email);
            if (Boolean.TRUE.equals(emUso)) {
                log.error("ADMIN_INICIAL_EMAIL já é usado por um usuário de outro tenant; administrador inicial não criado.");
                return null;
            }
            jdbc.update("INSERT INTO identity.usuarios (id, tenant_id, nome, email) VALUES (?, ?, ?, ?::citext)",
                    id, TENANT_DE_PRODUCAO, nome, email);
            int perfis = jdbc.update("""
                    INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                    SELECT ?, id FROM identity.perfis
                     WHERE tenant_id = ? AND nome = 'ADMINISTRADOR' AND sistema AND deleted_at IS NULL
                    """, id, TENANT_DE_PRODUCAO);
            if (perfis == 0) {
                throw new IllegalStateException("Tenant de produção sem o perfil ADMINISTRADOR.");
            }
            auditoria.registrar(TENANT_DE_PRODUCAO, null, null, "criar", "usuario", id, null,
                    Map.of("nome", nome, "email", email, "origem", "ADMIN_INICIAL_EMAIL"));
            return links.emitir(id, Tipo.CONVITE, validadeDoConvite);
        });
        if (convite == null) {
            return;
        }
        boolean enviado = envio.enviar(Tipo.CONVITE, nome, email, TENANT_DE_PRODUCAO, convite);
        log.info("Administrador inicial criado no tenant de produção; convite {}.",
                enviado ? "enviado por e-mail" : "NÃO enviado (SMTP falhou) — reenvie quando o e-mail estiver configurado");
    }
}
