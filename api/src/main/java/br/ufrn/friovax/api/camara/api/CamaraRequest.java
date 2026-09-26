package br.ufrn.friovax.api.camara.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

//campos e regras conforme docs/contrato-api.md mas se divergir da implementação muda.

@TemperaturaValida
public record CamaraRequest(

        @NotBlank
        @Size(min = 3, max = 20)
        String codigo,

        @NotBlank
        @Size(max = 100)
        String nome,

        @NotBlank
        @Size(max = 100)
        String unidade,

        @NotNull
        @Positive
        Integer capacidade,

        @NotNull
        BigDecimal temperaturaMinima,

        @NotNull
        BigDecimal temperaturaMaxima,

        @NotNull
        EstadoCamara estado
) {}
