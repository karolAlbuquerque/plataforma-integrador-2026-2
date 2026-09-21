package br.com.plataforma.identity.inicializacao;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.tenant.Tenants;

/**
 * Terceiro passo da subida: os dez perfis de sistema em todo tenant que ainda não os tenha
 * (Modelo §7). É código, não migration: a migration roda uma vez e não sabe quais tenants virão.
 * Perfil recém-criado recebe as permissões que o listam em perfisPadrao.
 */
@Component
@Order(3)
public class PerfisIniciais implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfisIniciais.class);

    public record PerfilDeSistema(String nome, String rotulo, String descricao) {
    }

    public static final List<PerfilDeSistema> PERFIS = List.of(
            new PerfilDeSistema("ADMINISTRADOR", "Administrador", "Todas as permissões, inclusive gestão de usuários e perfis"),
            new PerfilDeSistema("GESTOR", "Gestor", "Leitura ampla dos módulos; aprova desconto fora da política"),
            new PerfilDeSistema("VENDEDOR", "Vendedor", "CRM restrito às próprias oportunidades; propostas"),
            new PerfilDeSistema("PRE_VENDAS", "Pré-vendas", "Prospecção, importação e agendamento; não vê margem"),
            new PerfilDeSistema("FINANCEIRO", "Financeiro", "Financeiro completo; resumo de empresas e contratos"),
            new PerfilDeSistema("TECNICO", "Técnico", "Chamados e serviços; não vê margem nem valor de contrato"),
            new PerfilDeSistema("CONTABILIDADE", "Contabilidade", "Somente leitura do que a contabilidade precisa"),
            new PerfilDeSistema("MARKETING", "Marketing", "Campanhas, landing pages, formulários e automações"),
            new PerfilDeSistema("PARCEIRO", "Parceiro", "Apenas as próprias oportunidades registradas"),
            new PerfilDeSistema("CLIENTE", "Cliente", "Portal do cliente: chamados, cobranças e contratos próprios"));

    public static final Set<String> NOMES = PERFIS.stream().map(PerfilDeSistema::nome).collect(Collectors.toUnmodifiableSet());

    private final JdbcTemplate jdbc;
    private final Tenants tenants;
    private final TransactionTemplate transacao;

    public PerfisIniciais(JdbcTemplate jdbc, Tenants tenants, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.transacao = transacao;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int criados = tenants.todos().stream().mapToInt(this::garantirPerfis).sum();
        if (criados > 0) {
            log.info("Perfis de sistema criados: {}.", criados);
        }
    }

    /** @return quantos perfis foram criados agora (zero se o tenant já tinha os dez) */
    public int garantirPerfis(UUID tenant) {
        Integer criados = transacao.execute(status -> {
            int total = 0;
            for (PerfilDeSistema perfil : PERFIS) {
                List<UUID> novo = jdbc.query("""
                        INSERT INTO identity.perfis (id, tenant_id, nome, descricao, sistema)
                        VALUES (?, ?, ?, ?, true)
                        ON CONFLICT (tenant_id, nome) WHERE deleted_at IS NULL DO NOTHING
                        RETURNING id
                        """, (rs, linha) -> rs.getObject(1, UUID.class),
                        UUID.randomUUID(), tenant, perfil.nome(), perfil.descricao());
                if (!novo.isEmpty()) {
                    jdbc.update("""
                            INSERT INTO identity.perfil_permissoes (perfil_id, permissao_id)
                            SELECT ?, id FROM identity.permissoes WHERE ? = ANY(perfis_padrao)
                            ON CONFLICT DO NOTHING
                            """, novo.getFirst(), perfil.nome());
                    total++;
                }
            }
            return total;
        });
        return criados == null ? 0 : criados;
    }
}
