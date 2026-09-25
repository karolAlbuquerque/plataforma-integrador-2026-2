package br.com.plataforma.identity.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Célula do CSV: sem fórmula executável e com o separador protegido. */
class CsvDeAuditoriaTest {

    @Test
    void celulaQueViraFormulaGanhaApostrofo() {
        assertThat(CsvDeAuditoria.celula("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvDeAuditoria.celula("+55 62 9999")).isEqualTo("'+55 62 9999");
        assertThat(CsvDeAuditoria.celula("-2")).isEqualTo("'-2");
        assertThat(CsvDeAuditoria.celula("@SOMA(A1)")).isEqualTo("'@SOMA(A1)");
    }

    @Test
    void separadorAspasEQuebraDeLinhaVaoEntreAspas() {
        assertThat(CsvDeAuditoria.celula("a;b")).isEqualTo("\"a;b\"");
        assertThat(CsvDeAuditoria.celula("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
        assertThat(CsvDeAuditoria.celula("linha\nnova")).isEqualTo("\"linha\nnova\"");
    }

    @Test
    void valorComumSaiComoEsta() {
        assertThat(CsvDeAuditoria.celula("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(CsvDeAuditoria.celula(null)).isEmpty();
        assertThat(CsvDeAuditoria.celula("")).isEmpty();
    }
}
