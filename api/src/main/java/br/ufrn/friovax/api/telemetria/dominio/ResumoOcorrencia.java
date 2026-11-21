package br.ufrn.friovax.api.telemetria.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Projeção de consulta: não carrega evidências nem a fotografia dos lotes. */
public record ResumoOcorrencia(long id, long camaraId, int politicaVersao, OffsetDateTime abertaEm,
                              OffsetDateTime resolvidaEm, UUID resolvidaPor, String justificativa) {
    public EstadoOcorrencia estado() {
        return resolvidaEm == null ? EstadoOcorrencia.ABERTA : EstadoOcorrencia.RESOLVIDA;
    }

    public static ResumoOcorrencia de(Ocorrencia o) {
        return new ResumoOcorrencia(o.getId(), o.getCamaraId(), o.getPoliticaVersao(), o.getAbertaEm(),
                o.getResolvidaEm().orElse(null), o.getResolvidaPor().orElse(null), o.getJustificativa().orElse(null));
    }
}
