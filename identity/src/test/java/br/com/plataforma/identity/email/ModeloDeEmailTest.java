package br.com.plataforma.identity.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.plataforma.identity.email.ModeloDeEmail.EmailMontado;

/** Formato dos modelos de e-mail (emails/README.md do infra) — Requisito RF58. */
class ModeloDeEmailTest {

    private static final String COBRANCA = """
            Assunto: Cobrança {{numero}} vencida

            Olá, {{ nome }}.

            A cobrança {{numero}}, de {{valor}}, venceu.
            """;

    @Test
    void leAssuntoCorpoEAsVariaveisDasDuasPartes() {
        ModeloDeEmail modelo = ModeloDeEmail.ler("financeiro.cobranca-vencida", COBRANCA);
        assertThat(modelo.assunto()).isEqualTo("Cobrança {{numero}} vencida");
        assertThat(modelo.corpo()).startsWith("Olá, {{ nome }}.").endsWith("venceu.");
        assertThat(modelo.variaveis()).containsExactlyInAnyOrder("numero", "nome", "valor");
    }

    @Test
    void montaTrocandoAsVariaveis() {
        EmailMontado email = ModeloDeEmail.ler("m", COBRANCA)
                .montar(Map.of("numero", "C-12", "nome", " Ana ", "valor", "R$ 150,00", "sobra", "ignorada"));
        assertThat(email.assunto()).isEqualTo("Cobrança C-12 vencida");
        assertThat(email.corpo()).isEqualTo("Olá, Ana.\n\nA cobrança C-12, de R$ 150,00, venceu.");
    }

    @Test
    void valorComCifraoOuBarraEntraLiteral() {
        EmailMontado email = ModeloDeEmail.ler("m", "Assunto: {{a}}\n\n{{b}}")
                .montar(Map.of("a", "R$ 10", "b", "C:\\pasta\\$1"));
        assertThat(email.assunto()).isEqualTo("R$ 10");
        assertThat(email.corpo()).isEqualTo("C:\\pasta\\$1");
    }

    @Test
    void quebraDeLinhaNoAssuntoNaoViraCabecalhoNovo() {
        EmailMontado email = ModeloDeEmail.ler("m", "Assunto: Olá {{nome}}\n\nCorpo")
                .montar(Map.of("nome", "Ana\r\nBcc: alguem@exemplo.com"));
        assertThat(email.assunto()).isEqualTo("Olá Ana Bcc: alguem@exemplo.com").doesNotContain("\n", "\r");
    }

    @Test
    void variavelFaltandoOuEmBrancoRecusaOEnvio() {
        ModeloDeEmail modelo = ModeloDeEmail.ler("financeiro.cobranca-vencida", COBRANCA);
        assertThatThrownBy(() -> modelo.montar(Map.of("numero", "C-12", "valor", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("faltam as variáveis nome, valor do modelo financeiro.cobranca-vencida");
    }

    @Test
    void aceitaBomCrlfEAssuntoEmMinusculas() {
        ModeloDeEmail modelo = ModeloDeEmail.ler("m", "\uFEFFassunto: Oi\r\n\r\nLinha 1\r\nLinha 2\r\n\r\n");
        assertThat(modelo.assunto()).isEqualTo("Oi");
        assertThat(modelo.corpo()).isEqualTo("Linha 1\nLinha 2");
    }

    @Test
    void foraDoFormatoExplicaOMotivo() {
        assertThatThrownBy(() -> ModeloDeEmail.ler("m", "Olá\n\nCorpo"))
                .hasMessage("a primeira linha precisa ser 'Assunto: ...'");
        assertThatThrownBy(() -> ModeloDeEmail.ler("m", "Assunto:    \n\nCorpo"))
                .hasMessage("a primeira linha precisa ser 'Assunto: ...'");
        assertThatThrownBy(() -> ModeloDeEmail.ler("m", "Assunto: Oi\nCorpo colado"))
                .hasMessage("depois do assunto vem uma linha em branco e o corpo");
        assertThatThrownBy(() -> ModeloDeEmail.ler("m", "Assunto: Oi"))
                .hasMessage("depois do assunto vem uma linha em branco e o corpo");
        assertThatThrownBy(() -> ModeloDeEmail.ler("m", "Assunto: Oi\n\n  \n"))
                .hasMessage("o corpo está vazio");
    }

    @Test
    void enderecoMascaradoNoLog() {
        assertThat(CorreioDeSistema.mascarar("maria@centinela.com.br")).isEqualTo("m***@centinela.com.br");
        assertThat(CorreioDeSistema.mascarar("@centinela.com.br")).isEqualTo("***");
        assertThat(CorreioDeSistema.mascarar("sem-arroba")).isEqualTo("***");
    }
}
