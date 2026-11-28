package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico.DetalheOcorrencia;
import br.ufrn.friovax.api.telemetria.dominio.EstadoOcorrencia;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Histórico completo: evidências ordenadas e fotografia imutável dos lotes na abertura.")
public record OcorrenciaDetalheResponse(long id, long camaraId, EstadoOcorrencia estado, Instant abertaEm,
        PoliticaHistoricaResponse politica,
        @Schema(description = "Instante da resolução em UTC; nulo enquanto aberta.", nullable = true)
        Instant resolvidaEm,
        @Schema(description = "UUID do administrador responsável; nulo enquanto aberta.", nullable = true)
        UUID resolvidaPor,
        @Schema(description = "Justificativa do tratamento; nula enquanto aberta.", nullable = true)
        String justificativa,
        long leituraAberturaId, List<LeituraHistoricaResponse> evidencias, List<LoteAfetadoResponse> lotesAfetados,
        @Schema(description = "Leitura segura usada na resolução; nula enquanto aberta.", nullable = true)
        Long leituraResolucaoId) {
    static OcorrenciaDetalheResponse de(DetalheOcorrencia detalhe) {
        var o = OcorrenciaResponse.de(detalhe.resumo());
        return new OcorrenciaDetalheResponse(o.id(), o.camaraId(), o.estado(), o.abertaEm(), o.politica(),
                o.resolvidaEm(), o.resolvidaPor(), o.justificativa(), detalhe.leituraAberturaId(),
                detalhe.evidencias().stream().map(LeituraHistoricaResponse::de).toList(),
                detalhe.lotesAfetados().stream().map(l -> new LoteAfetadoResponse(l.loteId(), l.codigo(),
                        l.imunobiologico(), l.quantidade())).toList(), detalhe.leituraResolucaoId());
    }

    public record LoteAfetadoResponse(long loteId, String codigo, String imunobiologico, int quantidade) { }
}
