package br.com.plataforma.identity.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Descrição do dispositivo na tela de sessões ativas — Requisito RF06. */
class NavegadorTest {

    @Test
    void reconheceOsNavegadoresQueSeDizemChrome() {
        assertThat(Navegador.descrever("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")).isEqualTo("Chrome no Windows");
        assertThat(Navegador.descrever("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0")).isEqualTo("Edge no Windows");
        assertThat(Navegador.descrever("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 OPR/120.0")).isEqualTo("Opera no macOS");
    }

    @Test
    void celularVemPeloSistemaDoAparelho() {
        assertThat(Navegador.descrever("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1")).isEqualTo("Safari no iOS");
        assertThat(Navegador.descrever("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36")).isEqualTo("Chrome no Android");
        assertThat(Navegador.descrever("Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"))
                .isEqualTo("Firefox no Linux");
    }

    @Test
    void semReconhecerMostraOTextoCurto() {
        assertThat(Navegador.descrever("curl/8.9.1")).isEqualTo("curl/8.9.1");
        assertThat(Navegador.descrever("x".repeat(60))).isEqualTo("x".repeat(40) + "…");
        assertThat(Navegador.descrever("Algo (Windows NT 10.0)")).isEqualTo("Navegador no Windows");
        assertThat(Navegador.descrever(null)).isNull();
        assertThat(Navegador.descrever("  ")).isNull();
    }
}
