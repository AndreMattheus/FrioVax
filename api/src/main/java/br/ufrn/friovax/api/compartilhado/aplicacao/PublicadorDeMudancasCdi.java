package br.ufrn.friovax.api.compartilhado.aplicacao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Entrega o aviso como evento CDI ({@code @Observes MudancaConfirmada}) somente depois que a transação atual for
 * confirmada. Se ela for desfeita, o aviso é descartado. Assim nenhum observador precisa lembrar de usar
 * {@code TransactionPhase.AFTER_SUCCESS}: qualquer observador só vê mudanças que de fato foram gravadas.
 */
@ApplicationScoped
public class PublicadorDeMudancasCdi implements PublicadorDeMudancas {

    private final Event<MudancaConfirmada> eventos;
    private final TransactionSynchronizationRegistry transacoes;

    @Inject
    public PublicadorDeMudancasCdi(Event<MudancaConfirmada> eventos, TransactionSynchronizationRegistry transacoes) {
        this.eventos = eventos;
        this.transacoes = transacoes;
    }

    @Override
    public void publicar(MudancaConfirmada mudanca) {
        if (transacoes.getTransactionStatus() != Status.STATUS_ACTIVE) {
            throw new IllegalStateException("Uma mudança confirmada só pode ser publicada dentro de uma transação");
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
