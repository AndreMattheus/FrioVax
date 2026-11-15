package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;

import java.math.BigDecimal;
import java.util.Objects;

/** Versão da política térmica enviada na janela: faixa inclusiva e limiar N (ADR 0003, seção 2). */
public record PoliticaDaAnalise(int versao, BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, int limiar) {

    public PoliticaDaAnalise {
        if (versao < 1) {
            throw new IllegalArgumentException("A versão da política deve ser positiva.");
        }
        temperaturaMinima = LeituraDaAnalise.temperaturaEmEscalaUm(temperaturaMinima, "temperaturaMinima");
        temperaturaMaxima = LeituraDaAnalise.temperaturaEmEscalaUm(temperaturaMaxima, "temperaturaMaxima");
        if (temperaturaMinima.compareTo(temperaturaMaxima) >= 0) {
            throw new IllegalArgumentException("A temperatura mínima deve ser menor que a máxima.");
        }
        if (limiar < PoliticaTermica.LIMIAR_MINIMO || limiar > PoliticaTermica.LIMIAR_MAXIMO) {
            throw new IllegalArgumentException("O limiar deve estar entre " + PoliticaTermica.LIMIAR_MINIMO + " e "
                    + PoliticaTermica.LIMIAR_MAXIMO + ".");
        }
    }

    public static PoliticaDaAnalise de(PoliticaTermica politica) {
        Objects.requireNonNull(politica, "politica");
        return new PoliticaDaAnalise(politica.getVersao(), politica.getTemperaturaMinima(),
                politica.getTemperaturaMaxima(), politica.getLimiar());
    }
}
