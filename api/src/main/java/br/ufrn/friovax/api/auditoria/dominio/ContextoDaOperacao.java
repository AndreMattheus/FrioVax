package br.ufrn.friovax.api.auditoria.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem está agindo e em qual requisição. A implementação HTTP lê o JWT validado e o {@code X-Correlation-Id}; fora de
 * uma requisição autenticada, o ator é {@link Ator#SISTEMA} e a correlação é gerada pelo servidor.
 */
public interface ContextoDaOperacao {

    Ator ator();

    /** Sempre no formato de {@link EventoDeAuditoria#CORRELACAO}. */
    String correlacaoId();

    static ContextoDaOperacao doSistema() {
        return de(Ator.SISTEMA);
    }

    static ContextoDaOperacao de(Ator ator) {
        Objects.requireNonNull(ator, "ator");
        var correlacao = "sistema-" + UUID.randomUUID();
        return new ContextoDaOperacao() {
            @Override
            public Ator ator() {
                return ator;
            }

            @Override
            public String correlacaoId() {
                return correlacao;
            }
        };
    }
}
