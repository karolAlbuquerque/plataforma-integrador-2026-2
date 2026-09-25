package br.com.plataforma.identity.segundofator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** TOTP, Base32 e a cifra do segredo, sem Spring nem banco. */
class TotpTest {

    /** O segredo dos vetores de teste da RFC 6238 (apêndice B) para HMAC-SHA1. */
    private static final byte[] SEGREDO_DA_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /** A RFC dá oito dígitos; com seis, valem os seis últimos (o módulo é 10^6). */
    @ParameterizedTest
    @CsvSource({
            "59,          94287082",
            "1111111109,  07081804",
            "1111111111,  14050471",
            "1234567890,  89005924",
            "2000000000,  69279037",
            "20000000000, 65353130"
    })
    void codigosIguaisAosVetoresDaRfc6238(long segundos, String oitoDigitos) {
        long passo = Totp.passo(Instant.ofEpochSecond(segundos));
        assertThat(Totp.codigo(SEGREDO_DA_RFC, passo)).isEqualTo(oitoDigitos.substring(2));
    }

    @Test
    void aceitaUmPassoAntesEUmDepoisENadaAlem() {
        Instant agora = Instant.ofEpochSecond(1_234_567_890);
        long atual = Totp.passo(agora);
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual - 1), agora, null)).hasValue(atual - 1);
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual + 1), agora, null)).hasValue(atual + 1);
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual - 2), agora, null)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual + 2), agora, null)).isEmpty();
    }

    @Test
    void codigoJaUsadoOuAnteriorAoUltimoNaoVale() {
        Instant agora = Instant.ofEpochSecond(1_234_567_890);
        long atual = Totp.passo(agora);
        String codigo = Totp.codigo(SEGREDO_DA_RFC, atual);
        assertThat(Totp.verificar(SEGREDO_DA_RFC, codigo, agora, atual)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual - 1), agora, atual)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, Totp.codigo(SEGREDO_DA_RFC, atual + 1), agora, atual)).hasValue(atual + 1);
    }

    @Test
    void recusaFormatoErradoEAceitaEspacos() {
        Instant agora = Instant.ofEpochSecond(59);
        assertThat(Totp.verificar(SEGREDO_DA_RFC, "287 082", agora, null)).isPresent();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, "28708", agora, null)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, "abcdef", agora, null)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_DA_RFC, null, agora, null)).isEmpty();
    }

    /** RFC 4648 §10, sem o padding. */
    @ParameterizedTest
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    void base32ComoNaRfc4648(String texto, String codificado) {
        assertThat(Base32.codificar(texto.getBytes(StandardCharsets.US_ASCII))).isEqualTo(codificado);
        assertThat(new String(Base32.decodificar(codificado.toLowerCase() + "=="), StandardCharsets.US_ASCII)).isEqualTo(texto);
    }

    @Test
    void cifraDevolveOSegredoEDetectaChaveTrocada() {
        MockEnvironment producao = new MockEnvironment();
        CifraDeSegredo cifra = new CifraDeSegredo(Base64.getEncoder().encodeToString(new byte[32]), producao);
        byte[] segredo = Totp.novoSegredo();
        String gravado = cifra.cifrar(segredo);
        assertThat(cifra.decifrar(gravado)).isEqualTo(segredo);
        assertThat(cifra.cifrar(segredo)).as("nonce novo a cada cifra").isNotEqualTo(gravado);

        byte[] outraChave = new byte[32];
        outraChave[0] = 1;
        CifraDeSegredo outra = new CifraDeSegredo(Base64.getEncoder().encodeToString(outraChave), producao);
        assertThatThrownBy(() -> outra.decifrar(gravado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semChaveSoSobeNoPerfilDev() {
        assertThatThrownBy(() -> new CifraDeSegredo("", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SEGUNDO_FATOR_CHAVE");
        assertThatThrownBy(() -> new CifraDeSegredo(Base64.getEncoder().encodeToString(new byte[16]), new MockEnvironment()))
                .hasMessageContaining("32 bytes");
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        CifraDeSegredo cifra = new CifraDeSegredo("", dev);
        assertThat(cifra.decifrar(cifra.cifrar(new byte[] {1, 2, 3}))).containsExactly(1, 2, 3);
    }
}
