package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;

import java.math.BigDecimal;

public record AtualizarCamaraDTO(
        String nome,
        String unidade,
        int capacidade,
        BigDecimal temperaturaMinima,
        BigDecimal temperaturaMaxima,
        EstadoCamara estado,
        Integer versaoEsperada
) {
    public AtualizarCamaraDTO(String nome, String unidade, int capacidade, BigDecimal temperaturaMinima,
                              BigDecimal temperaturaMaxima, EstadoCamara estado) {
        this(nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima, estado, null);
    }
}
