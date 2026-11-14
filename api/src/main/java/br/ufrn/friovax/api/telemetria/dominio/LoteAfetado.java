package br.ufrn.friovax.api.telemetria.dominio;

import java.util.Objects;

public record LoteAfetado(long loteId, String codigo, String imunobiologico, int quantidade) {

    public LoteAfetado {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(imunobiologico, "imunobiologico");
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade do lote não pode ser negativa.");
        }
    }
}
