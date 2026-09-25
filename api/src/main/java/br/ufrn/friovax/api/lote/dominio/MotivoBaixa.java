package br.ufrn.friovax.api.lote.dominio;

/**
 * Motivo de uma baixa parcial (contrato, §4.2).
 */
public enum MotivoBaixa {
    /** Doses aplicadas em alguém. */
    ADMINISTRADA,
    /** Doses perdidas sem aplicação (quebra, contaminação). */
    PERDA
}
