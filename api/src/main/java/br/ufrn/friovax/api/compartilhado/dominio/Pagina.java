package br.ufrn.friovax.api.compartilhado.dominio;

import java.util.List;
import java.util.function.Function;

/**
 * Resultado paginado. {@code totalElementos} conta apenas os registros que atendem aos filtros.
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalElementos) {

    public Pagina {
        itens = List.copyOf(itens);
    }

    public static <T> Pagina<T> de(List<T> itens, Paginacao paginacao, long totalElementos) {
        return new Pagina<>(itens, paginacao.pagina(), paginacao.tamanho(), totalElementos);
    }

    public int totalPaginas() {
        return (int) ((totalElementos + tamanho - 1) / tamanho);
    }

    public <R> Pagina<R> map(Function<? super T, ? extends R> conversor) {
        List<R> convertidos = itens.stream().<R>map(conversor).toList();
        return new Pagina<>(convertidos, pagina, tamanho, totalElementos);
    }
}
