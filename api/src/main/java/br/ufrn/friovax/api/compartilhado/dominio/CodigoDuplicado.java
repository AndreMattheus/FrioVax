package br.ufrn.friovax.api.compartilhado.dominio;

public class CodigoDuplicado extends ConflitoDeNegocio {

    public CodigoDuplicado(String recurso, String codigo) {
        super("Já existe " + recurso + " com o código " + codigo + ".");
    }
}
