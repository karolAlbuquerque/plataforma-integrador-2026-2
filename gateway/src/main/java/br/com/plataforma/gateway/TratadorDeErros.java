package br.com.plataforma.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.List;
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

import br.com.plataforma.gateway.EscritorDeErro.Erro;
import reactor.core.publisher.Mono;

/**
 * Erro no caminho até o módulo vira resposta no envelope (Requisito RF48). Módulo fora do ar —
 * conexão recusada, container inexistente — responde 503; módulo que não responde em 3 segundos
 * (response-timeout do application.yml), 504. Nos dois casos o código do módulo vai em
 * errors[0].detalhe, para a tela sinalizar o que faltou e mostrar o resto (Contrato §9.6).
 * Roda antes do tratador padrão do Spring Boot (-1).
 */
@Component
@Order(-2)
public class TratadorDeErros implements ErrorWebExceptionHandler {

    static final String INDISPONIVEL = "MODULO_INDISPONIVEL";
    static final String SEM_RESPOSTA = "MODULO_SEM_RESPOSTA";
    /** Caminhos fora de /api, /public e /modulos são da casca. */
    static final String CASCA = "casca";

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
        String modulo = modulo(caminho);

        if (falhaDeConexao(erro)) {
            log.warn("{} indisponível ({}): {}", descrever(modulo), caminho, erro.toString());
            return erros.escrever(troca.getResponse(), 503, capitalizar(descrever(modulo)) + " indisponível.",
                    List.of(new Erro("modulo", INDISPONIVEL, modulo)));
        }
        if (erro instanceof ResponseStatusException status) {
            int codigo = status.getStatusCode().value();
            if (codigo == HttpStatus.GATEWAY_TIMEOUT.value()) {
                log.warn("{} não respondeu a tempo ({})", descrever(modulo), caminho);
                return erros.escrever(troca.getResponse(), codigo, capitalizar(descrever(modulo)) + " não respondeu a tempo.",
                        List.of(new Erro("modulo", SEM_RESPOSTA, modulo)));
            }
            return erros.escrever(troca.getResponse(), codigo,
                    codigo == 404 ? "Rota não encontrada." : "Requisição não atendida.");
        }
        log.error("Erro inesperado no gateway em {}", caminho, erro);
        return erros.escrever(troca.getResponse(), HttpStatus.BAD_GATEWAY.value(), "Erro ao encaminhar a requisição.");
    }

    /** "crm" para /api/crm/..., /public/crm/... e /modulos/crm/...; "casca" para o resto. */
    static String modulo(String caminho) {
        Matcher codigo = CODIGO_NO_CAMINHO.matcher(caminho);
        return codigo.find() ? codigo.group(1) : CASCA;
    }

    private static String descrever(String modulo) {
        return modulo.equals(CASCA) ? "plataforma" : "módulo " + modulo;
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
