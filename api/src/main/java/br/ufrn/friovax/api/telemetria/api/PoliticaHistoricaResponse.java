package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Política original, preservada mesmo após alterações da faixa vigente.")
public record PoliticaHistoricaResponse(int versao, BigDecimal temperaturaMinima,
                                        BigDecimal temperaturaMaxima, int limiar) {
    static PoliticaHistoricaResponse de(PoliticaTermica p) {
        return new PoliticaHistoricaResponse(p.getVersao(), p.getTemperaturaMinima(), p.getTemperaturaMaxima(),
                p.getLimiar());
    }
}
