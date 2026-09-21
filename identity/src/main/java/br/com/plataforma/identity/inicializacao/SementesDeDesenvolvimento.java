package br.com.plataforma.identity.inicializacao;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.inicializacao.PerfisIniciais.PerfilDeSistema;

/**
 * Quarto passo, só no perfil dev (Requisito RF60): dois tenants, um usuário por perfil em cada e
 * uma equipe "Comercial" — para qualquer grupo obter um token real sem esperar tela nenhuma.
 * Lista publicada em docs/usuarios-de-teste.md do infra. Idempotente: rodar de novo não duplica.
 */
@Component
@Profile("dev")
@Order(4)
public class SementesDeDesenvolvimento implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SementesDeDesenvolvimento.class);

    static final String SENHA = "Plataforma2026";

    record Empresa(UUID id, String nome, String dominio, UUID equipeComercial) {
    }

    static final Empresa EMPRESA_A = new Empresa(UUID.fromString("a0000000-0000-4000-8000-00000000000a"),
            "Empresa A", "empresa-a", UUID.fromString("a0000000-0000-4000-8000-0000000000e1"));
    static final Empresa EMPRESA_B = new Empresa(UUID.fromString("b0000000-0000-4000-8000-00000000000b"),
            "Empresa B", "empresa-b", UUID.fromString("b0000000-0000-4000-8000-0000000000e1"));

    private final JdbcTemplate jdbc;
    private final PerfisIniciais perfis;
    private final PasswordEncoder senhas;
    private final TransactionTemplate transacao;

    public SementesDeDesenvolvimento(JdbcTemplate jdbc, PerfisIniciais perfis, PasswordEncoder senhas,
                                     TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.perfis = perfis;
        this.senhas = senhas;
        this.transacao = transacao;
    }

    /** vendedor@empresa-a.dev, pre-vendas@empresa-b.dev... */
    static String emailDe(PerfilDeSistema perfil, Empresa empresa) {
        return perfil.nome().toLowerCase(Locale.ROOT).replace('_', '-') + "@" + empresa.dominio() + ".dev";
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        String hash = senhas.encode(SENHA);   // um hash só, reaproveitado: BCrypt 12 custa caro
        semear(EMPRESA_A, hash);
        semear(EMPRESA_B, hash);
        log.info("Perfil dev: tenants Empresa A e Empresa B com um usuário por perfil (senha {}).", SENHA);
    }

    private void semear(Empresa empresa, String hash) {
        jdbc.update("""
                INSERT INTO identity.tenants (id, razao_social, nome_fantasia, subdominio)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, empresa.id(), empresa.nome() + " Ltda.", empresa.nome(), empresa.dominio());
        perfis.garantirPerfis(empresa.id());

        transacao.executeWithoutResult(status -> {
            Map<String, UUID> usuarios = new HashMap<>();
            for (PerfilDeSistema perfil : PerfisIniciais.PERFIS) {
                String email = emailDe(perfil, empresa);
                // Id estável entre recriações do banco, derivado do e-mail
                UUID idPrevisto = UUID.nameUUIDFromBytes(("usuario:" + email).getBytes(StandardCharsets.UTF_8));
                jdbc.update("""
                        INSERT INTO identity.usuarios (id, tenant_id, nome, email, senha_hash)
                        VALUES (?, ?, ?, ?::citext, ?) ON CONFLICT DO NOTHING
                        """, idPrevisto, empresa.id(), perfil.rotulo() + " da " + empresa.nome(), email, hash);
                UUID id = jdbc.queryForObject(
                        "SELECT id FROM identity.usuarios WHERE email = ?::citext AND deleted_at IS NULL", UUID.class, email);
                jdbc.update("""
                        INSERT INTO identity.usuario_perfis (usuario_id, perfil_id)
                        SELECT ?, id FROM identity.perfis WHERE tenant_id = ? AND nome = ? AND deleted_at IS NULL
                        ON CONFLICT DO NOTHING
                        """, id, empresa.id(), perfil.nome());
                usuarios.put(perfil.nome(), id);
            }

            jdbc.update("INSERT INTO identity.equipes (id, tenant_id, nome) VALUES (?, ?, 'Comercial') ON CONFLICT DO NOTHING",
                    empresa.equipeComercial(), empresa.id());
            Map.of("VENDEDOR", false, "PRE_VENDAS", false, "GESTOR", true).forEach((perfil, lider) ->
                    jdbc.update("""
                            INSERT INTO identity.usuario_equipes (usuario_id, equipe_id, lider)
                            VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                            """, usuarios.get(perfil), empresa.equipeComercial(), lider));
        });
    }
}
