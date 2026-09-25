package br.com.plataforma.identity.auditoria;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import br.com.plataforma.identity.auditoria.ConsultaDeAuditoria.RegistroDeAuditoria;

/**
 * O CSV da exportação da auditoria: UTF-8 com BOM e separador ";", para abrir direto no Excel em
 * português. Célula que começa com =, +, - ou @ ganha um apóstrofo — senão a planilha a executaria
 * como fórmula (um nome de usuário "=HYPERLINK(...)" viraria link).
 */
final class CsvDeAuditoria {

    private static final String BOM = "﻿";
    private static final String CABECALHO =
            "ocorridoEm;usuario;usuarioId;ip;acao;entidade;entidadeId;valorAnterior;valorNovo";

    private CsvDeAuditoria() {
    }

    static byte[] gerar(List<RegistroDeAuditoria> registros) {
        StringBuilder csv = new StringBuilder(BOM).append(CABECALHO).append("\r\n");
        for (RegistroDeAuditoria registro : registros) {
            csv.append(String.join(";", Stream.of(
                    registro.ocorridoEm().toInstant().toString(),
                    registro.usuario() == null ? null : registro.usuario().nome(),
                    registro.usuario() == null ? null : registro.usuario().id().toString(),
                    registro.ip(),
                    registro.acao(),
                    registro.entidade(),
                    Objects.toString(registro.entidadeId(), null),
                    Objects.toString(registro.valorAnterior(), null),
                    Objects.toString(registro.valorNovo(), null))
                    .map(CsvDeAuditoria::celula)
                    .toList()));
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String celula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String texto = "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
