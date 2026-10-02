package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import java.util.List;

public record PaginaResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PaginaResponse<T> de(Pagina<T> pagina) {
        return new PaginaResponse<>(pagina.itens(), pagina.pagina(), pagina.tamanho(),
                pagina.totalElementos(), pagina.totalPaginas());
    }
}
