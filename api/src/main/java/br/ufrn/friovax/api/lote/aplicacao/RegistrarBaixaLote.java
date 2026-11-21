package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
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
    private final PublicadorDeMudancas publicador;

    @Inject
    public RegistrarBaixaLote(LoteRepository lotes, Clock relogio, PublicadorDeMudancas publicador) {
        this.lotes = lotes;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public RegistrarBaixaLote(LoteRepository lotes, Clock relogio) {
        this(lotes, relogio, PublicadorDeMudancas.nenhum());
    }

    @Transactional
    public Lote registrar(long id, int quantidade, MotivoBaixa motivo) {
        // Bloqueia só o lote: duas baixas simultâneas passam a ser aplicadas uma depois da outra, e a segunda já
        // enxerga a quantidade que sobrou. A baixa nunca aumenta a ocupação, então a câmara não precisa ser bloqueada.
        var lote = lotes.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
        lote.darBaixa(quantidade, motivo, OffsetDateTime.now(relogio));
        var salvo = lotes.salvar(lote);
        publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.LOTE, salvo.getId(), OperacaoAuditada.BAIXA,
                "Baixa de " + quantidade + " doses do lote " + salvo.getCodigo() + " (" + motivo + "); restam "
                        + salvo.getQuantidade(), OffsetDateTime.now(relogio)));
        return salvo;
    }
}
