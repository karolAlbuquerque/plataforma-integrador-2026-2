package br.com.plataforma.identity.autenticacao;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De onde veio a requisição, para o limite de tentativas e a auditoria.
 *
 * O IP é a última entrada do X-Forwarded-For — a que o gateway acrescenta com o endereço de quem
 * se conectou a ele. As anteriores vêm do cliente e podem ser inventadas. Sem o cabeçalho (chamada
 * direta ao identity, dentro da rede do Docker), vale o endereço da conexão.
 */
public record Origem(String ip, String userAgent) {

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    private static final Pattern CARACTERES_IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");
    private static final int MAXIMO_USER_AGENT = 500;

    public static Origem de(HttpServletRequest requisicao) {
        String agente = requisicao.getHeader("User-Agent");
        if (agente != null && agente.length() > MAXIMO_USER_AGENT) {
            agente = agente.substring(0, MAXIMO_USER_AGENT);
        }
        return new Origem(ip(requisicao), agente);
    }

    static String ip(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            String[] entradas = encaminhado.split(",");
            String ultima = normalizar(entradas[entradas.length - 1].strip());
            if (ultima != null) {
                return ultima;
            }
        }
        return normalizar(requisicao.getRemoteAddr());
    }

    /** Aceita só IP literal; nunca resolve nome (sem consulta de DNS). Inválido vira nulo. */
    static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        if (IPV4.matcher(valor).matches()) {
            return valor;
        }
        if (valor.contains(":") && CARACTERES_IPV6.matcher(valor).matches()) {
            try {
                // Com ":" o Java trata o texto como IPv6 literal e não consulta DNS
                return InetAddress.getByName(valor).getHostAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }
}
