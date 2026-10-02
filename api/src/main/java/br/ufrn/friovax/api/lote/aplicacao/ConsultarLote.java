package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarLote {
    private final LoteRepository lotes;

    @Inject
    public ConsultarLote(LoteRepository lotes) {
        this.lotes = lotes;
    }

    @Transactional
    public Lote consultar(long id) {
        // buscarPorId inclui registros inativos (contrato D11).
        return lotes.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
    }
}
