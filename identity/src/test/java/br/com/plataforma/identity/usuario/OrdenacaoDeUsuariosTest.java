package br.com.plataforma.identity.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.plataforma.identity.api.ErroDeNegocio;

/** O parâmetro "ordenar" vira ORDER BY só por uma lista branca: o texto do cliente nunca chega ao SQL. */
class OrdenacaoDeUsuariosTest {

    @Test
    void colunasDaListaBrancaComDirecao() {
        assertThat(UsuarioConsultas.clausulaDeOrdem(null)).isEqualTo("lower(x.nome) asc NULLS LAST");
        assertThat(UsuarioConsultas.clausulaDeOrdem("")).isEqualTo("lower(x.nome) asc NULLS LAST");
        assertThat(UsuarioConsultas.clausulaDeOrdem("email,desc")).isEqualTo("x.email desc NULLS LAST");
        assertThat(UsuarioConsultas.clausulaDeOrdem("ultimoLoginEm")).isEqualTo("x.ultimo_login_em asc NULLS LAST");
        assertThat(UsuarioConsultas.clausulaDeOrdem(" criadoEm , DESC ")).isEqualTo("x.created_at desc NULLS LAST");
    }

    @Test
    void qualquerOutraCoisaERecusada() {
        for (String ordenar : new String[] {"senha_hash,asc", "nome,asc;DROP TABLE identity.usuarios",
                "nome,asc,id", "nome desc", "nome,para-cima", "x.nome"}) {
            assertThatThrownBy(() -> UsuarioConsultas.clausulaDeOrdem(ordenar))
                    .as(ordenar)
                    .isInstanceOf(ErroDeNegocio.class)
                    .hasMessageContaining("Ordene por nome, email, ultimoLoginEm ou criadoEm");
        }
    }
}
