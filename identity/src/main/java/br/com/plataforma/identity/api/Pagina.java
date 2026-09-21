package br.com.plataforma.identity.api;

import java.util.List;

/** Formato de toda listagem paginada (Contrato §8.3). */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {
}
