package br.ufrn.friovax.api.camara.dominio;

/** A versão só identifica uma política quando acompanhada da câmara. */
public record ReferenciaPolitica(long camaraId, int versao) {
}
