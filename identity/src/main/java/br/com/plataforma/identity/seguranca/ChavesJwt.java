package br.com.plataforma.identity.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * A chave RS256 que assina todo token da plataforma (Contrato §4.2). A privada só existe aqui; a
 * pública sai no JWKS. O identity valida os próprios tokens com a mesma chave, sem chamar HTTP.
 */
@Configuration
public class ChavesJwt {

    public static final String AUDIENCIA = "plataforma";

    private static final Logger log = LoggerFactory.getLogger(ChavesJwt.class);

    @Bean
    RSAKey chaveDeAssinatura(@Value("${identity.jwt.chave-privada:}") String chaveEmBase64,
                             @Value("${identity.jwt.kid}") String kid,
                             Environment ambiente) throws GeneralSecurityException, JOSEException {
        if (chaveEmBase64 == null || chaveEmBase64.isBlank()) {
            if (!ambiente.matchesProfiles("dev")) {
                throw new IllegalStateException(
                        "JWT_CHAVE_PRIVADA não definida. Gere com scripts/gerar-env.sh do infra-integrador-2026.");
            }
            log.warn("JWT_CHAVE_PRIVADA vazia: usando chave efêmera. Os tokens deixam de valer quando o serviço reiniciar.");
            return new RSAKeyGenerator(2048).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
        }
        return lerChave(chaveEmBase64, kid);
    }

    /**
     * A variável traz o PEM inteiro ("BEGIN PRIVATE KEY", PKCS#8) codificado em base64 numa linha
     * só, porque .env não aceita quebra de linha. A pública é derivada da privada.
     */
    public static RSAKey lerChave(String chaveEmBase64, String kid) throws GeneralSecurityException {
        String pem = new String(Base64.getMimeDecoder().decode(chaveEmBase64.strip()), StandardCharsets.US_ASCII);
        String corpo = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        KeyFactory fabrica = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privada = (RSAPrivateCrtKey) fabrica.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(corpo)));
        RSAPublicKey publica = (RSAPublicKey) fabrica.generatePublic(
                new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));
        return new RSAKey.Builder(publica)
                .privateKey(privada)
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    @Bean
    JwtEncoder codificadorDeToken(RSAKey chave) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(chave)));
    }

    /** Valida assinatura, validade, emissor e audiência — as mesmas regras dos módulos. */
    @Bean
    JwtDecoder decodificadorDeToken(RSAKey chave, @Value("${identity.jwt.emissor}") String emissor) throws JOSEException {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withPublicKey(chave.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtClaimValidator<List<String>> audiencia =
                new JwtClaimValidator<>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCIA));
        decodificador.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(emissor), audiencia));
        return decodificador;
    }
}
