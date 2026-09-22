package br.com.plataforma.identity.senha;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.email.CorreioDeSistema;
import br.com.plataforma.identity.email.ModeloDeEmail;
import br.com.plataforma.identity.email.ModelosDeEmail;
import br.com.plataforma.identity.senha.LinksDeSenha.LinkEmitido;
import br.com.plataforma.identity.senha.LinksDeSenha.Tipo;
import br.com.plataforma.identity.tenant.Tenants;

/**
 * Manda o link de convite ou de recuperação. Sai direto daqui, e não pela fila
 * identity.email.enviar: o Contrato §9.7 proíbe token em mensagem, que fica na fila, na .dlq e
 * em log.
 *
 * O token vai no fragmento da URL (#token=...): o navegador não o envia ao servidor, então ele não
 * aparece em log do gateway nem do nginx, nem no Referer.
 */
@Component
public class EnvioDeLinks {

    private static final DateTimeFormatter VALIDADE = DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ModelosDeEmail modelos;
    private final CorreioDeSistema correio;
    private final Tenants tenants;
    private final String urlPublica;

    public EnvioDeLinks(ModelosDeEmail modelos, CorreioDeSistema correio, Tenants tenants,
                        @Value("${identity.url-publica}") String urlPublica) {
        this.modelos = modelos;
        this.correio = correio;
        this.tenants = tenants;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    /** @return false se o SMTP falhou — o link continua valendo e pode ser reenviado */
    public boolean enviar(Tipo tipo, String nome, String email, UUID tenant, LinkEmitido link) {
        ModeloDeEmail modelo = modelos.interno(tipo == Tipo.CONVITE ? ModelosDeEmail.CONVITE : ModelosDeEmail.RECUPERACAO);
        Map<String, String> variaveis = Map.of(
                "nome", nome,
                "email", email,
                "empresa", tenants.nome(tenant).orElse("plataforma"),
                "link", urlPublica + "/definir-senha#token=" + link.token(),
                "validade", VALIDADE.format(link.expiraEm()) + " (horário de Brasília)");
        return correio.tentarEnviar(email, modelo.montar(variaveis), modelo.nome());
    }
}
