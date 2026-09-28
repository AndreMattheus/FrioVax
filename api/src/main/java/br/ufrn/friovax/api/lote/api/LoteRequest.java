package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LoteRequest(

    @NotBlank
    String codigo,

    @NotBlank
    @Size(max = 100)
    String imunobiologico,

    @NotBlank
    @Size(max = 100)
    String fabricante,

    @NotNull
    LocalDate validade,

    @NotNull
    @Positive
    Integer quantidade,

    @NotNull
    Long camaraId
) {}
