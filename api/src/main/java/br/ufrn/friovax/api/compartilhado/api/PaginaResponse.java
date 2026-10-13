package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página de resultados ordenada por `id` crescente. Uma página além da última volta com "
    + "`items` vazio.")
public record PaginaResponse<T>(
        @Schema(description = "Registros da página.")
        List<T> items,
        @Schema(description = "Número da página, a partir de 0.", examples = "0")
        int page,
        @Schema(description = "Tamanho solicitado da página.", examples = "20")
        int size,
        @Schema(description = "Total de registros que atendem aos filtros.", examples = "1")
        long totalElements,
        @Schema(description = "Total de páginas para os filtros e o tamanho informados.", examples = "1")
        int totalPages) {
    public static <T> PaginaResponse<T> de(Pagina<T> pagina) {
        return new PaginaResponse<>(pagina.itens(), pagina.pagina(), pagina.tamanho(),
                pagina.totalElementos(), pagina.totalPaginas());
    }
}
