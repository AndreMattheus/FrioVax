package br.ufrn.friovax.api.auditoria.dominio;

/**
 * Quem está agindo e em qual requisição. A implementação HTTP lê o JWT validado e o {@code X-Correlation-Id}; fora de
 * uma requisição autenticada, o ator é {@link Ator#SISTEMA} e a correlação é gerada pelo servidor.
 */
public interface ContextoDaOperacao {

    Ator ator();

    /** Sempre no formato de {@link EventoDeAuditoria#CORRELACAO}. */
    String correlacaoId();
}
