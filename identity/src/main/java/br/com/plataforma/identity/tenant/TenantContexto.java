package br.com.plataforma.identity.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant da operação em andamento. Preenchido pelo {@code TenantFiltro} a partir do token, ou
 * pelo consumidor de mensagens a partir do campo tenantId do envelope — nunca do corpo da
 * requisição (Contrato §6).
 *
 * O identity usa SQL explícito, sem o filtro automático do Hibernate: toda consulta a dado de
 * tenant chama {@link #exigir()} e filtra tenant_id à mão.
 */
public final class TenantContexto {

    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();

    private TenantContexto() {
    }

    public static Optional<UUID> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    public static UUID exigir() {
        return atual().orElseThrow(() -> new IllegalStateException("Nenhum tenant no contexto da operação."));
    }

    public static void definir(UUID tenant) {
        ATUAL.set(Objects.requireNonNull(tenant, "tenant"));
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public static void executar(UUID tenant, Runnable acao) {
        calcular(tenant, () -> {
            acao.run();
            return null;
        });
    }

    public static <T> T calcular(UUID tenant, Supplier<T> acao) {
        UUID anterior = ATUAL.get();
        definir(tenant);
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                limpar();
            } else {
                ATUAL.set(anterior);
            }
        }
    }
}
