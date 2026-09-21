package br.com.plataforma.identity.observabilidade;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Coloca o X-Request-Id gerado pelo gateway em todo log da requisição (Requisito RF47), para que
 * um erro relatado possa ser seguido pelos vários serviços que participaram.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacaoFiltro extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String CHAVE_MDC = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String id = Optional.ofNullable(requisicao.getHeader(CABECALHO))
                .filter(valor -> !valor.isBlank() && valor.length() <= 100)
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(CHAVE_MDC, id);
        resposta.setHeader(CABECALHO, id);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
