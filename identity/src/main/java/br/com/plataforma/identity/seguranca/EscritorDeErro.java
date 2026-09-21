package br.com.plataforma.identity.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.plataforma.identity.api.Resposta;
import jakarta.servlet.http.HttpServletResponse;

/** Escreve erros de segurança no mesmo envelope das outras respostas. */
public class EscritorDeErro {

    private final ObjectMapper mapper;

    public EscritorDeErro(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escrever(HttpServletResponse resposta, int status, String mensagem) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(resposta.getOutputStream(), Resposta.falha(mensagem));
    }
}
