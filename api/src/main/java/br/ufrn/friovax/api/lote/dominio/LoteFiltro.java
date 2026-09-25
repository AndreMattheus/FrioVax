package br.ufrn.friovax.api.lote.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Textos;

import java.time.LocalDate;

/**
 * Filtros da listagem de lotes (contrato, §4.1), combinados com E lógico. Campos {@code null} não filtram.
 *
 * @param imunobiologico busca parcial sem diferenciar maiúsculas/minúsculas
 * @param validadeDe     limite inferior inclusivo (D10)
 * @param validadeAte    limite superior inclusivo (D10)
 * @param camaraId       igualdade; câmara inexistente resulta em página vazia
 * @param estado         igualdade
 * @param ativo          {@code true} lista só ativos; {@code false}, só inativos
 */
public record LoteFiltro(String imunobiologico, LocalDate validadeDe, LocalDate validadeAte, Long camaraId,
                         EstadoLote estado, boolean ativo) {

    public LoteFiltro {
        imunobiologico = Textos.opcional(imunobiologico);
        if (validadeDe != null && validadeAte != null && validadeDe.isAfter(validadeAte)) {
            throw new IllegalArgumentException("validadeDe deve ser anterior ou igual a validadeAte");
        }
    }

    public static LoteFiltro ativos() {
        return new LoteFiltro(null, null, null, null, null, true);
    }
}
