package br.ufrn.friovax.api.telemetria.dominio;

import java.util.Objects;

public record OcorrenciaFiltro(Long camaraId, EstadoOcorrencia estado, PeriodoConsulta periodo) {
    public OcorrenciaFiltro {
        Objects.requireNonNull(periodo, "periodo");
        if (camaraId != null && camaraId <= 0) {
            throw new IllegalArgumentException("camaraId deve ser positivo");
        }
    }
}
