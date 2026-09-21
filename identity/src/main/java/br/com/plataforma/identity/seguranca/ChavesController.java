package br.com.plataforma.identity.seguranca;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Chave pública no formato da RFC 7517, sem envelope, para o Spring dos módulos ler sem
 * configuração (Contrato §4.2). É a única resposta do identity fora do envelope.
 */
@RestController
public class ChavesController {

    private final Map<String, Object> conjunto;

    public ChavesController(RSAKey chave) {
        this.conjunto = new JWKSet(chave.toPublicJWK()).toJSONObject();
    }

    @GetMapping(value = "/api/identity/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chavesPublicas() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(conjunto);
    }
}
