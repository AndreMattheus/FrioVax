package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocio;

public abstract class ConflitoDeResolucao extends ConflitoDeNegocio {

    private final long ocorrenciaId;

    protected ConflitoDeResolucao(long ocorrenciaId, String mensagem) {
        super(mensagem);
        this.ocorrenciaId = ocorrenciaId;
    }

    public long ocorrenciaId() {
        return ocorrenciaId;
    }
}
