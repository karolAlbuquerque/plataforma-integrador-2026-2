package br.com.plataforma.identity.senha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import br.com.plataforma.identity.api.ErroCampo;
import br.com.plataforma.identity.api.ErroDeNegocio;

/** Regra de senha nova — Requisito RF09. */
class PoliticaDeSenhaTest {

    @Test
    void senhaComOitoCaracteresLetraENumeroPassa() {
        assertThat(PoliticaDeSenha.problema("senha123")).isEmpty();
        assertThat(PoliticaDeSenha.problema("ação2026")).isEmpty();   // letra acentuada é letra
    }

    @Test
    void curtaDemaisOuSemLetraOuSemNumeroNaoPassa() {
        assertThat(PoliticaDeSenha.problema(null)).contains("A senha precisa ter pelo menos 8 caracteres.");
        assertThat(PoliticaDeSenha.problema("abc1234")).contains("A senha precisa ter pelo menos 8 caracteres.");
        assertThat(PoliticaDeSenha.problema("abcdefgh")).contains("A senha precisa ter letras e números.");
        assertThat(PoliticaDeSenha.problema("12345678")).contains("A senha precisa ter letras e números.");
    }

    @Test
    void tamanhoContaCaracteresENaoUnidadesDoJava() {
        // Cada emoji ocupa duas unidades UTF-16: sete caracteres de verdade não bastam
        assertThat(PoliticaDeSenha.problema("😀😀😀😀😀a1")).contains("A senha precisa ter pelo menos 8 caracteres.");
        assertThat(PoliticaDeSenha.problema("😀😀😀😀😀😀a1")).isEmpty();
    }

    @Test
    void acimaDe72BytesORecusaEmVezDeOBCryptCortarEmSilencio() {
        assertThat(PoliticaDeSenha.problema("a1" + "x".repeat(70))).isEmpty();
        assertThat(PoliticaDeSenha.problema("a1" + "é".repeat(36)))   // 2 + 72 bytes em UTF-8
                .contains("A senha pode ter até 72 bytes; use uma mais curta.");
    }

    @Test
    void exigirRecusaCom400SemRepetirASenha() {
        assertThatThrownBy(() -> PoliticaDeSenha.exigir("minhasenhafraca", "novaSenha"))
                .isInstanceOfSatisfying(ErroDeNegocio.class, erro -> {
                    assertThat(erro.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    ErroCampo campo = erro.comoErroDeCampo();
                    assertThat(campo.campo()).isEqualTo("novaSenha");
                    assertThat(campo.codigo()).isEqualTo("SENHA_FRACA");
                    assertThat(campo.detalhe()).doesNotContain("minhasenhafraca");
                });
    }
}
