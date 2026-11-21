package br.ufrn.friovax.api.telemetria.dominio;

import java.time.OffsetDateTime;

/** Intervalo de instantes [de, ate), com limites opcionais. */
public record PeriodoConsulta(OffsetDateTime de, OffsetDateTime ate) {
    public PeriodoConsulta {
        if (de != null && ate != null && !de.isBefore(ate)) {
            throw new IllegalArgumentException("de deve ser anterior a ate");
        }
    }

    public boolean contem(OffsetDateTime instante) {
        return (de == null || !instante.isBefore(de)) && (ate == null || instante.isBefore(ate));
    }
}
