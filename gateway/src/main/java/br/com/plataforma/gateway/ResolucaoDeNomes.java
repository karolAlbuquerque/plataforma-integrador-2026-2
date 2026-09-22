package br.com.plataforma.gateway;

import java.time.Duration;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Como o gateway encontra o endereço de cada módulo na rede do Docker. Dois ajustes no resolvedor
 * do Netty, que por padrão espera 5 s por consulta e guarda a resposta pelo TTL do DNS:
 *
 * <ul>
 *   <li>1 s por consulta. Módulo que não está no compose não tem nome na rede, e o DNS embutido
 *       encaminha a pergunta para o DNS do host: o "não existe" chega em até 4 s — mais que o tempo
 *       limite de 3 s (RF48), e o módulo fora do ar viraria 504 em vez de 503.</li>
 *   <li>Endereço guardado por no máximo 10 s. O DNS do Docker responde com TTL de 600 s; recriado,
 *       o container ganha outro IP, e o gateway continuaria batendo no antigo por 10 minutos —
 *       503 "indisponível" para um módulo que já está no ar.</li>
 * </ul>
 */
@Configuration
public class ResolucaoDeNomes {

    static final Duration TEMPO_POR_CONSULTA = Duration.ofSeconds(1);
    static final Duration VALIDADE_DO_ENDERECO = Duration.ofSeconds(10);

    @Bean
    HttpClientCustomizer resolucaoRapida() {
        return cliente -> cliente.resolver(resolvedor -> resolvedor
                .queryTimeout(TEMPO_POR_CONSULTA)
                .maxQueriesPerResolve(2)
                .cacheMaxTimeToLive(VALIDADE_DO_ENDERECO));
    }
}
