package br.ufrn.friovax.api.compartilhado.api;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/** Correlação da requisição HTTP atual, definida pelo {@link CorrelacaoFilter}. */
@RequestScoped
public class CorrelacaoDaRequisicao {
    private final CurrentVertxRequest requisicao;

    @Inject
    public CorrelacaoDaRequisicao(CurrentVertxRequest requisicao) {
        this.requisicao = requisicao;
    }

    public String id() {
        // Um contexto CDI ativo também pode existir em tarefas internas, sem requisição HTTP.
        var contexto = requisicao.getCurrent();
        return contexto == null ? null : contexto.get(CorrelacaoFilter.CABECALHO);
    }
}
