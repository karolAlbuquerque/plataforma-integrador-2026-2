package br.com.plataforma.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.nimbusds.jose.jwk.RSAKey;

import br.com.plataforma.identity.autenticacao.Origem;
import br.com.plataforma.identity.seguranca.ChavesJwt;

/** Partes sem banco: leitura da chave do .env e origem da requisição. */
class UnidadesTest {

    @Test
    void chaveNoFormatoDoGerarEnvELidaEAPublicaDerivada() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        KeyPair par = gerador.generateKeyPair();

        // Como o scripts/gerar-env.sh: PEM PKCS#8 inteiro, em base64 numa linha só
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(par.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String variavel = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));

        RSAKey chave = ChavesJwt.lerChave(variavel, "prod-2026");
        assertThat(chave.getKeyID()).isEqualTo("prod-2026");
        assertThat(chave.isPrivate()).isTrue();
        assertThat(chave.toRSAPublicKey().getModulus()).isEqualTo(((RSAPublicKey) par.getPublic()).getModulus());
    }

    @Test
    void ipEOUltimoDoXForwardedFor() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setRemoteAddr("172.18.0.5");
        requisicao.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        assertThat(Origem.de(requisicao).ip()).isEqualTo("203.0.113.9");
    }

    @Test
    void ipInvalidoNoCabecalhoCaiParaOEnderecoDaConexao() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setRemoteAddr("172.18.0.5");
        requisicao.addHeader("X-Forwarded-For", "servidor.exemplo.com");
        assertThat(Origem.de(requisicao).ip()).isEqualTo("172.18.0.5");

        requisicao = new MockHttpServletRequest();
        requisicao.setRemoteAddr("172.18.0.5");
        requisicao.addHeader("X-Forwarded-For", "999.1.1.1");
        assertThat(Origem.de(requisicao).ip()).isEqualTo("172.18.0.5");
    }

    @Test
    void ipv6EAceito() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Forwarded-For", "2001:db8::1");
        assertThat(Origem.de(requisicao).ip()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }
}
