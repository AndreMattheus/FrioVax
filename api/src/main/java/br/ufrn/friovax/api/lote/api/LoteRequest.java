package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

//Campos e regras conforme docs/contrato-api.md mas se divergir da implementação muda.
 /*
 * Importante: @Future usa o relógio padrão do sistema (fuso da JVM),
 * não o fuso America/Fortaleza combinado em D9. Se precisar dps trocar por um @ConstraintValidator
 * customizado usando o Clock injetável mencionado na ADR 0001, quando ele existir.
 */
public record LoteRequest(

        @NotBlank
        @Size(max = 40)
        String codigo,

        @NotBlank
        @Size(max = 100)
        String imunobiologico,

        @NotBlank
        @Size(max = 100)
        String fabricante,

        @NotNull
        @Future
        LocalDate validade,

        @NotNull
        @Positive
        Integer quantidade,

        @NotNull
        Long camaraId
) {}
