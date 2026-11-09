package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observador de teste: guarda os avisos de mudança confirmada que chegaram. Usa um {@code @Observes} comum, sem fase
 * de transação, para provar que o publicador só entrega o aviso depois do commit.
 */
@ApplicationScoped
public class ColetorDeMudancas {
    private final List<MudancaConfirmada> recebidas = new CopyOnWriteArrayList<>();

    void receber(@Observes MudancaConfirmada mudanca) {
        recebidas.add(mudanca);
    }

    public List<MudancaConfirmada> recebidasDe(String recurso, long recursoId) {
        return recebidas.stream()
                .filter(m -> m.recurso().equals(recurso) && m.recursoId() == recursoId)
                .toList();
    }
}
