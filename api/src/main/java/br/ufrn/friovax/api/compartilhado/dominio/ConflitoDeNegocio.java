package br.ufrn.friovax.api.compartilhado.dominio;

/**
 * Operação válida em formato, mas incompatível com o estado atual dos dados.
 */
public abstract class ConflitoDeNegocio extends ExcecaoDeDominio {

    protected ConflitoDeNegocio(String mensagem) {
        super(mensagem);
    }
}
