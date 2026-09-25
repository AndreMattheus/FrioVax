package br.ufrn.friovax.api.compartilhado.dominio;

public class RecursoNaoEncontrado extends ExcecaoDeDominio {

    public RecursoNaoEncontrado(String recurso, long id) {
        super(recurso + " " + id + " não encontrado(a).");
    }
}
