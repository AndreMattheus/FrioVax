package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Baixa parcial de doses de um lote (contrato, §4.2). A ocupação da câmara é a soma das quantidades dos lotes ativos
 * (D6), então reduzir a quantidade já libera a capacidade correspondente.
 */
@ApplicationScoped
public class RegistrarBaixaLote {
    private final LoteRepository lotes;
    private final Clock relogio;

    @Inject
    public RegistrarBaixaLote(LoteRepository lotes, Clock relogio) {
        this.lotes = lotes;
        this.relogio = relogio;
    }

    @Transactional
    public Lote registrar(long id, int quantidade, MotivoBaixa motivo) {
        // Bloqueia só o lote: duas baixas simultâneas passam a ser aplicadas uma depois da outra, e a segunda já
        // enxerga a quantidade que sobrou. A baixa nunca aumenta a ocupação, então a câmara não precisa ser bloqueada.
        var lote = lotes.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
        lote.darBaixa(quantidade, motivo, OffsetDateTime.now(relogio));
        return lotes.salvar(lote);
    }
}
