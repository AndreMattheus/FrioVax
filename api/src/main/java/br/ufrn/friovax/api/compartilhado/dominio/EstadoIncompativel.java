package br.ufrn.friovax.api.compartilhado.dominio;

public class EstadoIncompativel extends ConflitoDeNegocio {

    public EstadoIncompativel(String mensagem) {
        super(mensagem);
    }
}
