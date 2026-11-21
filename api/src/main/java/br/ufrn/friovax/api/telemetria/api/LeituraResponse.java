package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico.LeituraHistorica;
import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Medição e resultado confirmado, sem reanálise. Instantes em UTC (Z).")
public record LeituraResponse(long id, long camaraId, String idExterno, String origem, BigDecimal temperatura,
                              Instant instante, Instant aceitaEm, PoliticaHistoricaResponse politica,
                              ClassificacaoTermica classificacao, int sequencia, boolean limiarAtingido) {
    static LeituraResponse de(LeituraHistorica historica) {
        var l = historica.leitura();
        return new LeituraResponse(l.getId(), l.getCamaraId(), l.getIdExterno(), l.getOrigem(), l.getTemperatura(),
                l.getInstante().toInstant(), l.getAceitaEm().toInstant(), PoliticaHistoricaResponse.de(historica.politica()),
                l.getResultado().classificacao(), l.getResultado().sequencia(), l.getResultado().limiarAtingido());
    }
}
