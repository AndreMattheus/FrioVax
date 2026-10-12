package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Descarte total de um lote (contrato, §4.2). O lote descartado continua ativo e ocupando a câmara até ser retirado
 * fisicamente e inativado (DELETE), que é quando a capacidade é liberada (D6).
 */
@ApplicationScoped
public class DescartarLote {
    private final LoteRepository lotes;
    private final Clock relogio;

    @Inject
    public DescartarLote(LoteRepository lotes, Clock relogio) {
        this.lotes = lotes;
        this.relogio = relogio;
    }

    @Transactional
    public Lote descartar(long id) {
        var lote = lotes.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
        // Repetir o descarte não altera nada (o domínio ignora); salvar o mesmo estado é inofensivo.
        lote.descartar(OffsetDateTime.now(relogio));
        return lotes.salvar(lote);
    }
}
