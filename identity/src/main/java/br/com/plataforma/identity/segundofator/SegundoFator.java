package br.com.plataforma.identity.segundofator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * O segundo fator de cada usuário (Requisito RF10): segredo TOTP cifrado em usuarios.mfa_secret,
 * último passo aceito em mfa_ultimo_passo e os códigos de recuperação.
 *
 * Obrigatório para todo usuário (decisão de 25/09), menos no perfil dev (IDENTITY_EXIGIR_SEGUNDO_FATOR
 * vazio ou false), para os usuários de teste dos outros grupos entrarem com um POST só (RF60). Mesmo desligado, quem já cadastrou
 * continua precisando do código. Não existe "desativar": só trocar de aparelho ou o administrador
 * redefinir, o que força um cadastro novo no próximo login.
 *
 * Todo método que mexe no segredo espera uma transação aberta por quem chama.
 */
@Service
public class SegundoFator {

    public record Situacao(boolean ativo, Instant ativadoEm, int codigosRestantes, boolean obrigatorio) {
    }

    /** O que o usuário leva para o autenticador: o segredo em Base32 e a URI do QR Code. */
    public record Cadastro(String segredo, String uri) {
    }

    private static final Logger log = LoggerFactory.getLogger(SegundoFator.class);

    private record Gravado(String segredo, Long ultimoPasso, String pendente) {
    }

    private final JdbcTemplate jdbc;
    private final CifraDeSegredo cifra;
    private final CodigosDeRecuperacao codigos;
    private final boolean obrigatorio;
    private final String emissor;

    /**
     * @param obrigatorio "true", "false" ou vazio — vazio desliga no perfil dev e liga nos demais, para
     *                    um .env antigo, sem a variável, não mudar o comportamento de ninguém
     */
    public SegundoFator(JdbcTemplate jdbc, CifraDeSegredo cifra, CodigosDeRecuperacao codigos,
                        @Value("${identity.segundo-fator.obrigatorio:}") String obrigatorio,
                        @Value("${identity.segundo-fator.emissor}") String emissor, Environment ambiente) {
        this.jdbc = jdbc;
        this.cifra = cifra;
        this.codigos = codigos;
        this.obrigatorio = obrigatorio == null || obrigatorio.isBlank()
                ? !ambiente.matchesProfiles("dev")
                : Boolean.parseBoolean(obrigatorio.strip());
        this.emissor = emissor;
        log.info("Segundo fator {}.", this.obrigatorio ? "obrigatório para todos os usuários"
                : "não obrigatório (quem já cadastrou continua usando)");
    }

    public boolean obrigatorio() {
        return obrigatorio;
    }

    public Situacao situacao(UUID usuario) {
        return jdbc.queryForObject("SELECT mfa_ativo, mfa_ativado_em FROM identity.usuarios WHERE id = ?",
                (rs, linha) -> {
                    OffsetDateTime ativadoEm = rs.getObject("mfa_ativado_em", OffsetDateTime.class);
                    return new Situacao(rs.getBoolean("mfa_ativo"), ativadoEm == null ? null : ativadoEm.toInstant(),
                            codigos.restantes(usuario), obrigatorio);
                }, usuario);
    }

    /** Segredo novo, já cifrado para gravar, e o que mostrar ao usuário. */
    public record SegredoNovo(String cifrado, Cadastro cadastro) {
    }

    public SegredoNovo novoSegredo(String email) {
        byte[] segredo = Totp.novoSegredo();
        return new SegredoNovo(cifra.cifrar(segredo), cadastro(email, segredo));
    }

    /** Reconstrói o que mostrar a partir de um segredo cifrado já gravado (a tela foi recarregada). */
    public Cadastro cadastroDe(String email, String segredoCifrado) {
        return cadastro(email, cifra.decifrar(segredoCifrado));
    }

    /** Confere um código contra um segredo ainda não ativado (cadastro ou troca de aparelho). */
    public OptionalLong conferirProvisorio(String segredoCifrado, String codigo) {
        return Totp.verificar(cifra.decifrar(segredoCifrado), codigo, Instant.now(), null);
    }

    /**
     * Confere o código do aplicativo contra o segredo ativo, travando a linha do usuário: dois
     * pedidos com o mesmo código não passam juntos, e o passo aceito vira o último usado.
     */
    public boolean conferir(UUID usuario, String codigo) {
        Gravado gravado = travar(usuario);
        if (gravado.segredo() == null) {
            return false;
        }
        OptionalLong passo = Totp.verificar(cifra.decifrar(gravado.segredo()), codigo, Instant.now(), gravado.ultimoPasso());
        if (passo.isEmpty()) {
            return false;
        }
        jdbc.update("UPDATE identity.usuarios SET mfa_ultimo_passo = ? WHERE id = ?", passo.getAsLong(), usuario);
        return true;
    }

    public boolean usarCodigoDeRecuperacao(UUID usuario, String codigo) {
        travar(usuario);
        return codigos.usar(usuario, codigo);
    }

    /**
     * Ativa o segredo conferido e gera os dez códigos de recuperação, que o usuário vê uma vez.
     * Serve ao primeiro cadastro e à troca de aparelho: o segredo anterior deixa de valer.
     */
    public List<String> ativar(UUID usuario, String segredoCifrado, long passoConferido) {
        jdbc.update("""
                UPDATE identity.usuarios
                   SET mfa_ativo = true, mfa_secret = ?, mfa_secret_pendente = NULL, mfa_ativado_em = now(),
                       mfa_ultimo_passo = ?, updated_at = now()
                 WHERE id = ?
                """, segredoCifrado, passoConferido, usuario);
        return codigos.gerarNovos(usuario);
    }

    public void guardarPendente(UUID usuario, String segredoCifrado) {
        jdbc.update("UPDATE identity.usuarios SET mfa_secret_pendente = ? WHERE id = ?", segredoCifrado, usuario);
    }

    public String pendente(UUID usuario) {
        return travar(usuario).pendente();
    }

    public List<String> novosCodigos(UUID usuario) {
        return codigos.gerarNovos(usuario);
    }

    /** Apaga tudo: o próximo login cai no cadastro obrigatório (ou entra direto, se não for obrigatório). */
    public void redefinir(UUID usuario, UUID autor) {
        jdbc.update("""
                UPDATE identity.usuarios
                   SET mfa_ativo = false, mfa_secret = NULL, mfa_secret_pendente = NULL, mfa_ativado_em = NULL,
                       mfa_ultimo_passo = NULL, updated_at = now(), updated_by = ?
                 WHERE id = ?
                """, autor, usuario);
        codigos.apagar(usuario);
    }

    private Gravado travar(UUID usuario) {
        return jdbc.queryForObject("""
                SELECT mfa_secret, mfa_ultimo_passo, mfa_secret_pendente FROM identity.usuarios WHERE id = ? FOR UPDATE
                """, (rs, linha) -> new Gravado(rs.getString("mfa_secret"), rs.getObject("mfa_ultimo_passo", Long.class),
                rs.getString("mfa_secret_pendente")), usuario);
    }

    /** otpauth://totp/Emissor:email?secret=...&issuer=Emissor — o formato que os autenticadores leem. */
    private Cadastro cadastro(String email, byte[] segredo) {
        String base32 = Base32.codificar(segredo);
        String rotulo = codificar(emissor) + ":" + codificar(email);
        String uri = "otpauth://totp/" + rotulo + "?secret=" + base32 + "&issuer=" + codificar(emissor)
                + "&algorithm=SHA1&digits=" + Totp.DIGITOS + "&period=" + Totp.PASSO_EM_SEGUNDOS;
        return new Cadastro(base32, uri);
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
