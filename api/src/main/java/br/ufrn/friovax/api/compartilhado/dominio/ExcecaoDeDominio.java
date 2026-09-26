package br.ufrn.friovax.api.compartilhado.dominio;

/**
 * Base das exceções lançadas pelas regras de negócio. A camada de API converte cada subtipo em Problem Details.
 */
public abstract class ExcecaoDeDominio extends RuntimeException {

    protected ExcecaoDeDominio(String mensagem) {
        super(mensagem);
    }
}
