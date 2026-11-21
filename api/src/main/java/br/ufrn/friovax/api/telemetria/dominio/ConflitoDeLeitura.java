package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocio;

/** Conflitos próprios da recepção de leituras, que a API traduz em {@code 409} com um tipo específico. */
public abstract class ConflitoDeLeitura extends ConflitoDeNegocio {

    private final long camaraId;

    protected ConflitoDeLeitura(long camaraId, String mensagem) {
        super(mensagem);
        this.camaraId = camaraId;
    }

    public long camaraId() {
        return camaraId;
    }
}
