package br.ufrn.friovax.api.compartilhado.aplicacao;

import br.ufrn.friovax.api.auditoria.aplicacao.RegistrarAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.dominio.PublicadorDeMudancaPersistida;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.time.OffsetDateTime;

/**
 * Entrega o aviso como evento CDI ({@code @Observes MudancaConfirmada}) somente depois que a transação atual for
 * confirmada. Se ela for desfeita, o aviso é descartado. Assim nenhum observador precisa lembrar de usar
 * {@code TransactionPhase.AFTER_SUCCESS}: qualquer observador só vê mudanças que de fato foram gravadas.
 *
 * <p>Mudanças auditadas ({@link MudancaConfirmada#auditavel()}) também são gravadas na trilha de auditoria agora, na
 * mesma transação: um rollback desfaz a alteração e o evento juntos (US10).
 */
@ApplicationScoped
public class PublicadorDeMudancasCdi implements PublicadorDeMudancas, PublicadorDeMudancaPersistida {

    private final Event<MudancaConfirmada> eventos;
    private final TransactionSynchronizationRegistry transacoes;
    private final RegistrarAuditoria auditoria;

    @Inject
    public PublicadorDeMudancasCdi(Event<MudancaConfirmada> eventos, TransactionSynchronizationRegistry transacoes,
                                   RegistrarAuditoria auditoria) {
        this.eventos = eventos;
        this.transacoes = transacoes;
        this.auditoria = auditoria;
    }

    @Override
    public void publicar(String recurso, long id, String descricao, OffsetDateTime instante) {
        publicar(new MudancaConfirmada(recurso, id, descricao, null, instante));
    }

    @Override
    public void publicar(MudancaConfirmada mudanca) {
        if (transacoes.getTransactionStatus() != Status.STATUS_ACTIVE) {
            throw new IllegalStateException("Uma mudança confirmada só pode ser publicada dentro de uma transação");
        }
        if (mudanca.auditavel()) {
            auditoria.registrar(RecursoAuditado.doAviso(mudanca.recurso()), mudanca.recursoId(), mudanca.operacao(),
                    mudanca.descricao(), mudanca.instante());
        }
        transacoes.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // nada a fazer antes do commit
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    eventos.fire(mudanca);
                }
            }
        });
    }
}
