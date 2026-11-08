package br.ufrn.friovax.api.compartilhado.aplicacao;

/**
 * Publica avisos de {@link MudancaConfirmada}. A implementação real ({@link PublicadorDeMudancasCdi}) entrega o aviso
 * somente depois do commit; os testes sem banco usam implementações simples que só registram as chamadas.
 */
public interface PublicadorDeMudancas {

    void publicar(MudancaConfirmada mudanca);

    static PublicadorDeMudancas nenhum() {
        return mudanca -> { };
    }
}
