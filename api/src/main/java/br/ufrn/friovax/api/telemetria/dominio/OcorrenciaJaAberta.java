package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;

public class OcorrenciaJaAberta extends EstadoIncompativel {
    public OcorrenciaJaAberta(long camaraId) {
        super("A câmara " + camaraId + " já possui uma ocorrência térmica aberta.");
    }
}
