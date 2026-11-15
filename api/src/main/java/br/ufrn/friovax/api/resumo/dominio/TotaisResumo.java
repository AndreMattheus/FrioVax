package br.ufrn.friovax.api.resumo.dominio;

import java.util.Map;

public record TotaisResumo(Map<String, Long> camarasPorEstadoOperacional,
                           Map<String, Long> camarasPorEstadoTermico,
                           Map<String, Estoque> lotesPorEstado,
                           long episodiosAbertos, long lotesDistintosAfetados) {
    public TotaisResumo {
        camarasPorEstadoOperacional = Map.copyOf(camarasPorEstadoOperacional);
        camarasPorEstadoTermico = Map.copyOf(camarasPorEstadoTermico);
        lotesPorEstado = Map.copyOf(lotesPorEstado);
    }

    public record Estoque(long lotes, long doses) { }
}
