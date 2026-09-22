package br.com.plataforma.identity.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.Usuarios;
import br.com.plataforma.identity.tenant.TenantContexto;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Grava na auditoria o 403 — autenticado, mas sem a permissão (Requisito RF49). Só o método e o
 * caminho, sem a query string: um parâmetro pode trazer dado pessoal. Falha ao gravar não muda a
 * resposta ao cliente.
 */
@Component
public class RegistroDeAcessoNegado {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeAcessoNegado.class);
    private static final int MAXIMO_CAMINHO = 300;

    private final Auditoria auditoria;

    public RegistroDeAcessoNegado(Auditoria auditoria) {
        this.auditoria = auditoria;
    }

    public void registrar(HttpServletRequest requisicao) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            return;
        }
        try {
            Jwt jwt = token.getToken();
            String caminho = requisicao.getRequestURI();
            Map<String, Object> detalhes = new LinkedHashMap<>();
            detalhes.put("metodo", requisicao.getMethod());
            detalhes.put("caminho", caminho.length() > MAXIMO_CAMINHO ? caminho.substring(0, MAXIMO_CAMINHO) : caminho);
            UUID usuario = null;
            if (Usuarios.ehServico(jwt)) {
                detalhes.put("servico", jwt.getSubject());
            } else {
                usuario = Usuarios.idDe(jwt);
            }
            auditoria.registrar(TenantContexto.atual().orElse(null), usuario, Origem.de(requisicao).ip(),
                    "acesso_negado", "rota", null, detalhes);
        } catch (RuntimeException e) {
            log.warn("Acesso negado não registrado na auditoria: {}", e.getMessage());
        }
    }
}
