package br.ufrn.friovax.api.telemetria.dominio;

import java.util.List;
import java.util.Objects;

/** Resposta da análise: mesma correlação e um resultado por janela, na ordem da solicitação. */
public record ResultadoDaAnalise(String correlacaoId, List<ResultadoDaJanela> janelas) {

    public ResultadoDaAnalise {
        Objects.requireNonNull(correlacaoId, "correlacaoId");
        janelas = List.copyOf(Objects.requireNonNull(janelas, "janelas"));
    }
}
