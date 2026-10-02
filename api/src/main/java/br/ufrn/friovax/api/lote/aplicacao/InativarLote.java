package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;

@ApplicationScoped
public class InativarLote {
    private final LoteRepository lotes;
    private final Clock relogio;

    @Inject
    public InativarLote(LoteRepository lotes, Clock relogio) {
        this.lotes = lotes;
        this.relogio = relogio;
    }

    @Transactional
    public void inativar(long id) {
        var lote = lotes.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
        if (!lote.isAtivo()) {
            return;
        }
        // A ocupação é a soma dos lotes ativos; preservar quantidade e vínculo basta para liberar capacidade.
        lote.inativar(OffsetDateTime.now(relogio));
        lotes.salvar(lote);
    }
}
