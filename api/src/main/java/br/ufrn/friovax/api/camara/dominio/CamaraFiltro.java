package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Textos;

/**
 * Filtros da listagem de câmaras (contrato, §4.1), combinados com E lógico. Campos {@code null} não filtram.
 *
 * @param unidade igualdade sem diferenciar maiúsculas/minúsculas
 * @param estado  igualdade
 * @param ativo   {@code true} lista só ativas; {@code false}, só inativas
 */
public record CamaraFiltro(String unidade, EstadoCamara estado, boolean ativo) {

    public CamaraFiltro {
        unidade = Textos.opcional(unidade);
    }

    public static CamaraFiltro ativas() {
        return new CamaraFiltro(null, null, true);
    }
}
