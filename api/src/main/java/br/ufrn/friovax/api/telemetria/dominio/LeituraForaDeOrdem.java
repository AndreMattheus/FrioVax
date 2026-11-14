package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;

public class LeituraForaDeOrdem extends EstadoIncompativel {
    public LeituraForaDeOrdem(long camaraId) {
        super("A leitura deve ser posterior à última leitura aceita da câmara " + camaraId + ".");
    }
}
