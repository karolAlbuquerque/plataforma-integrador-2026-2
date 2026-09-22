package br.com.plataforma.identity.email;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Fila curta, em memória, para o que não pode segurar a resposta — o pedido de recuperação de
 * senha responde antes de procurar o e-mail, para o tempo de resposta não revelar se ele existe.
 * (O nome da classe difere do bean de propósito: iguais, colidem.)
 */
@Configuration
public class ExecucaoEmSegundoPlano {

    @Bean
    Executor tarefasDeEmail() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("email-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);   // além disso o pedido é recusado e só aparece no log
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
