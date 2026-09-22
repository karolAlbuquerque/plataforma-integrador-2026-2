package br.com.plataforma.gateway;

import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/** Erros do próprio gateway no mesmo envelope dos módulos (Contrato §8.2). */
@Component
public class EscritorDeErro {

    record Resposta(boolean success, Object data, String message, List<Erro> errors) {
    }

    /** O ErroDeCampo do contrato: { campo, codigo, detalhe }. */
    record Erro(String campo, String codigo, String detalhe) {
    }

    private final ObjectMapper mapper;

    public EscritorDeErro(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Mono<Void> escrever(ServerHttpResponse resposta, int status, String mensagem) {
        return escrever(resposta, status, mensagem, List.of());
    }

    public Mono<Void> escrever(ServerHttpResponse resposta, int status, String mensagem, List<Erro> erros) {
        byte[] corpo;
        try {
            corpo = mapper.writeValueAsBytes(new Resposta(false, null, mensagem, erros));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        resposta.setStatusCode(HttpStatusCode.valueOf(status));
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        resposta.getHeaders().setContentLength(corpo.length);
        return resposta.writeWith(Mono.just(resposta.bufferFactory().wrap(corpo)));
    }
}
