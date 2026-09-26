package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.List;

final class Paginador {

    private Paginador() {
    }

    /** Recorta uma lista já filtrada e ordenada, como o banco faria com LIMIT/OFFSET. */
    static <T> Pagina<T> paginar(List<T> ordenados, Paginacao paginacao) {
        var itens = ordenados.stream()
                .skip(paginacao.deslocamento())
                .limit(paginacao.tamanho())
                .toList();
        return Pagina.de(itens, paginacao, ordenados.size());
    }
}
