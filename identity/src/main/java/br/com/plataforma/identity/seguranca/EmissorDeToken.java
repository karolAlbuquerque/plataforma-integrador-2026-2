package br.com.plataforma.identity.seguranca;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import br.com.plataforma.identity.usuario.UsuarioAutenticado;

/** Monta e assina os dois tipos de token da plataforma (Contrato §4.2 e §9.2). */
@Component
public class EmissorDeToken {

    private final JwtEncoder codificador;
    private final String kid;
    private final String emissor;
    private final Duration validade;

    public EmissorDeToken(JwtEncoder codificador,
                          @Value("${identity.jwt.kid}") String kid,
                          @Value("${identity.jwt.emissor}") String emissor,
                          @Value("${identity.jwt.validade}") Duration validade) {
        this.codificador = codificador;
        this.kid = kid;
        this.emissor = emissor;
        this.validade = validade;
    }

    /** Token de usuário: sempre com tenant_id (Requisito RF03). */
    public TokenEmitido paraUsuario(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = base(agora)
                .subject(usuario.id().toString())
                .claim("tenant_id", usuario.tenantId().toString())
                .claim("nome", usuario.nome())
                .claim("email", usuario.email())
                .claim("roles", usuario.perfis())
                .claim("perms", usuario.permissoes())
                .claim("equipes", usuario.equipes().stream().map(UUID::toString).toList())
                .build();
        return assinar(claims, agora);
    }

    /** Token de serviço: sub = svc:{modulo}, sem tenant_id — o tenant vai em X-Tenant-Id. */
    public TokenEmitido paraServico(String clientId, List<String> permissoes) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = base(agora)
                .subject("svc:" + clientId)
                .claim("roles", List.of())
                .claim("perms", permissoes)
                .build();
        return assinar(claims, agora);
    }

    private JwtClaimsSet.Builder base(Instant agora) {
        return JwtClaimsSet.builder()
                .issuer(emissor)
                .audience(List.of(ChavesJwt.AUDIENCIA))
                .issuedAt(agora)
                .expiresAt(agora.plus(validade));
    }

    private TokenEmitido assinar(JwtClaimsSet claims, Instant agora) {
        JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        String valor = codificador.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenEmitido(valor, validade.toSeconds(), agora.plus(validade));
    }
}
