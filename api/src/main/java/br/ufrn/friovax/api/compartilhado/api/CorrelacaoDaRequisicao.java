package br.ufrn.friovax.api.compartilhado.api;

import jakarta.enterprise.context.RequestScoped;

/** Correlação da requisição HTTP atual, definida pelo {@link CorrelacaoFilter}. */
@RequestScoped
public class CorrelacaoDaRequisicao {
    private String id;

    public String id() {
        return id;
    }

    public void definir(String id) {
        this.id = id;
    }
}
