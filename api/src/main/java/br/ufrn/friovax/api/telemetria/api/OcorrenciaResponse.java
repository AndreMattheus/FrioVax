package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico.OcorrenciaHistorica;
import br.ufrn.friovax.api.telemetria.dominio.EstadoOcorrencia;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Resumo sem evidências e lotes; tratamento reflete os dados persistidos.")
public record OcorrenciaResponse(long id, long camaraId, EstadoOcorrencia estado, Instant abertaEm,
                                 PoliticaHistoricaResponse politica, Instant resolvidaEm,
                                 UUID resolvidaPor, String justificativa) {
    static OcorrenciaResponse de(OcorrenciaHistorica historica) {
        var o = historica.ocorrencia();
        return new OcorrenciaResponse(o.id(), o.camaraId(), o.estado(), o.abertaEm().toInstant(),
                PoliticaHistoricaResponse.de(historica.politica()),
                o.resolvidaEm() == null ? null : o.resolvidaEm().toInstant(), o.resolvidaPor(), o.justificativa());
    }
}
