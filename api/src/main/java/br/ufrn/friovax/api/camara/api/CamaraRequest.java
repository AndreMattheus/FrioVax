package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@TemperaturaValida
public record CamaraRequest(

    @NotBlank
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
