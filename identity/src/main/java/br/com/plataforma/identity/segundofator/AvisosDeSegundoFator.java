package br.com.plataforma.identity.segundofator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.plataforma.identity.email.CorreioDeSistema;
import br.com.plataforma.identity.email.ModeloDeEmail;
import br.com.plataforma.identity.email.ModelosDeEmail;
import br.com.plataforma.identity.tenant.Tenants;

/**
 * Avisa o dono da conta de toda mudança no segundo fator e de todo código de recuperação usado:
 * se não foi ele, é o sinal de que a senha e o celular (ou o papel) caíram em outras mãos.
 * Sai direto do identity, depois do commit; falha de SMTP fica só no log.
 */
@Component
public class AvisosDeSegundoFator {

    static final String ATIVADO = "segundo-fator-ativado";
    static final String REDEFINIDO = "segundo-fator-redefinido";
    static final String CODIGO_USADO = "codigo-recuperacao-usado";

    private static final DateTimeFormatter QUANDO = DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ModelosDeEmail modelos;
    private final CorreioDeSistema correio;
    private final Tenants tenants;

    public AvisosDeSegundoFator(ModelosDeEmail modelos, CorreioDeSistema correio, Tenants tenants) {
        this.modelos = modelos;
        this.correio = correio;
        this.tenants = tenants;
    }

    public void ativado(String nome, String email, UUID tenant) {
        enviar(ATIVADO, nome, email, tenant, Map.of());
    }

    public void redefinido(String nome, String email, UUID tenant) {
        enviar(REDEFINIDO, nome, email, tenant, Map.of());
    }

    public void codigoUsado(String nome, String email, UUID tenant, int restantes) {
        enviar(CODIGO_USADO, nome, email, tenant, Map.of("restantes", String.valueOf(restantes)));
    }

    private void enviar(String nomeDoModelo, String nome, String email, UUID tenant, Map<String, String> extras) {
        ModeloDeEmail modelo = modelos.interno(nomeDoModelo);
        Map<String, String> variaveis = new HashMap<>(extras);
        variaveis.put("nome", nome);
        variaveis.put("empresa", tenants.nome(tenant).orElse("plataforma"));
        variaveis.put("quando", QUANDO.format(Instant.now()) + " (horário de Brasília)");
        correio.tentarEnviar(email, modelo.montar(variaveis), modelo.nome());
    }
}
