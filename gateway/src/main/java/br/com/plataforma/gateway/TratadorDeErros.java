package br.com.plataforma.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Erro no caminho até o módulo vira resposta no envelope. Módulo fora do ar — conexão recusada,
 * container inexistente — responde 503 "Módulo {codigo} indisponível.", e a casca mostra isso em
 * vez de uma página de erro do servidor. Roda antes do tratador padrão do Spring Boot (-1).
 */
@Component
@Order(-2)
public class TratadorDeErros implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);
    private static final Pattern CODIGO_NO_CAMINHO = Pattern.compile("^/(?:api|public|modulos)/([a-z]+)(?:/|$)");

    private final EscritorDeErro erros;

    public TratadorDeErros(EscritorDeErro erros) {
        this.erros = erros;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange troca, Throwable erro) {
        if (troca.getResponse().isCommitted()) {
            return Mono.error(erro);
        }
        String caminho = troca.getRequest().getPath().value();
        String destino = destino(caminho);

        if (falhaDeConexao(erro)) {
            log.warn("{} indisponível ({}): {}", destino, caminho, erro.toString());
            return erros.escrever(troca.getResponse(), 503, capitalizar(destino) + " indisponível.");
        }
        if (erro instanceof ResponseStatusException status) {
            int codigo = status.getStatusCode().value();
            String mensagem = switch (codigo) {
                case 404 -> "Rota não encontrada.";
                case 504 -> capitalizar(destino) + " não respondeu a tempo.";
                default -> "Requisição não atendida.";
            };
            return erros.escrever(troca.getResponse(), codigo, mensagem);
        }
        log.error("Erro inesperado no gateway em {}", caminho, erro);
        return erros.escrever(troca.getResponse(), HttpStatus.BAD_GATEWAY.value(), "Erro ao encaminhar a requisição.");
    }

    static String destino(String caminho) {
        Matcher codigo = CODIGO_NO_CAMINHO.matcher(caminho);
        return codigo.find() ? "módulo " + codigo.group(1) : "plataforma";
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static boolean falhaDeConexao(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConnectException || causa instanceof UnknownHostException
                    || causa instanceof ClosedChannelException) {
                return true;
            }
        }
        return false;
    }
}
