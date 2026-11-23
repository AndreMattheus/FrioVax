package br.ufrn.friovax.api.camara.aplicacao;

import java.math.BigDecimal;

public record AlterarPoliticaTermicaDTO(
        int versaoEsperada,
        BigDecimal temperaturaMinima,
        BigDecimal temperaturaMaxima,
        int limiar
) {}
