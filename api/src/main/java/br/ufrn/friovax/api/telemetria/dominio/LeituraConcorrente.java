package br.ufrn.friovax.api.telemetria.dominio;

/**
 * Os dados usados na análise (última leitura, política ou início do episódio) mudaram antes da confirmação, mesmo
 * depois da única nova tentativa (ADR 0003, seção 5). Nenhuma leitura foi gravada; o cliente pode reenviar.
 */
public class LeituraConcorrente extends ConflitoDeLeitura {

    public LeituraConcorrente(long camaraId) {
        super(camaraId, "Os dados da câmara " + camaraId
                + " mudaram durante a análise térmica e a leitura não foi confirmada; reenvie a leitura.");
    }
}
