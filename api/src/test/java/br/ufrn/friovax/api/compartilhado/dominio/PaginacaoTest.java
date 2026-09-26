package br.ufrn.friovax.api.compartilhado.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginacaoTest {

    @Test
    void deveUsarPadroesDoContrato() {
        var paginacao = Paginacao.padrao();

        assertEquals(0, paginacao.pagina());
        assertEquals(20, paginacao.tamanho());
        assertEquals(40, new Paginacao(2, 20).deslocamento());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void deveRejeitarValoresForaDoIntervalo(int pagina, int tamanho) {
        assertThrows(IllegalArgumentException.class, () -> new Paginacao(pagina, tamanho));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "20,1", "21,2", "100,5"})
    void deveCalcularTotalDePaginas(long totalElementos, int totalPaginas) {
        assertEquals(totalPaginas, new Pagina<>(List.of(), 0, 20, totalElementos).totalPaginas());
    }

    @Test
    void deveConverterItensMantendoMetadados() {
        var pagina = Pagina.de(List.of(1, 2), new Paginacao(1, 2), 5).map(String::valueOf);

        assertEquals(List.of("1", "2"), pagina.itens());
        assertEquals(1, pagina.pagina());
        assertEquals(5, pagina.totalElementos());
    }
}
