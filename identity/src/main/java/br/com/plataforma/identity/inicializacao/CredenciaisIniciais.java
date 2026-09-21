package br.com.plataforma.identity.inicializacao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.plataforma.identity.autenticacao.CredenciaisDeServico;
import br.com.plataforma.identity.autenticacao.CredenciaisDeServico.Credencial;

/**
 * Segundo passo da subida: uma credencial de serviço para cada SVC_{MODULO}_SEGREDO presente no
 * ambiente (Contrato §9.2). As permissões do token de serviço são refeitas a cada subida, a partir
 * do campo "servicos" das listas de permissões — só depois do catálogo carregado.
 */
@Component
@Order(2)
public class CredenciaisIniciais implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredenciaisIniciais.class);

    private final CredenciaisDeServico credenciais;
    private final PasswordEncoder senhas;
    private final Environment ambiente;
    private final TransactionTemplate transacao;
    private final List<String> modulos;

    public CredenciaisIniciais(CredenciaisDeServico credenciais, PasswordEncoder senhas, Environment ambiente,
                               TransactionTemplate transacao, @Value("${identity.servicos.modulos}") List<String> modulos) {
        this.credenciais = credenciais;
        this.senhas = senhas;
        this.ambiente = ambiente;
        this.transacao = transacao;
        this.modulos = modulos;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        List<String> resumo = new ArrayList<>();
        for (String modulo : modulos) {
            String segredo = ambiente.getProperty("SVC_" + modulo.strip().toUpperCase(Locale.ROOT) + "_SEGREDO");
            if (segredo == null || segredo.isBlank()) {
                continue;
            }
            Integer permissoes = transacao.execute(status -> {
                Optional<Credencial> existente = credenciais.buscar(modulo);
                UUID id;
                if (existente.isEmpty()) {
                    id = credenciais.criar(modulo, senhas.encode(segredo));
                } else {
                    id = existente.get().id();
                    if (!senhas.matches(segredo, existente.get().segredoHash())) {
                        credenciais.trocarSegredo(id, senhas.encode(segredo));   // o .env foi regenerado
                    }
                }
                return credenciais.reconstruirPermissoes(id, modulo);
            });
            resumo.add(modulo + " (" + permissoes + ")");
        }
        log.info("Credenciais de serviço prontas, com o número de permissões: {}.",
                resumo.isEmpty() ? "nenhuma" : String.join(", ", resumo));
    }
}
