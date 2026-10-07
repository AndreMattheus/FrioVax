package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

// O código é imutável. Zero só é aceito para manter a quantidade de um lote já esgotado.
public record AtualizarLoteRequest(
    @NotBlank(message = "deve ser informado") String imunobiologico,
    @NotBlank(message = "deve ser informado") String fabricante,
    @NotNull(message = "deve ser informada") LocalDate validade,
    @NotNull(message = "deve ser informada")
    @PositiveOrZero(message = "não pode ser negativa") Integer quantidade,
    @NotNull(message = "deve ser informado") Long camaraId
) {}
