package br.ufrn.friovax.api.lote.dominio;

/**
 * Estado do lote (contrato, §3.4). Nunca é informado pelo cliente.
 */
public enum EstadoLote {
    DISPONIVEL,
    ESGOTADO,
    DESCARTADO
}
