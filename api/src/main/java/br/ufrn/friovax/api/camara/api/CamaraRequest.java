package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@TemperaturaValida
public record CamaraRequest(

    @NotBlank(message = "deve ser informado")
    String codigo,

    @NotBlank(message = "deve ser informado")
    String nome,

    @NotBlank(message = "deve ser informada")
    String unidade,

    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer capacidade,

    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMinima,

    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMaxima,

    @NotNull(message = "deve ser informado")
    EstadoCamara estado
) {}
