package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

// O tamanho dos textos é verificado no domínio, depois do trim (contrato, §2.3).
public record LoteRequest(

    @NotBlank(message = "deve ser informado")
    String codigo,

    @NotBlank(message = "deve ser informado")
    String imunobiologico,

    @NotBlank(message = "deve ser informado")
    String fabricante,

    @NotNull(message = "deve ser informada")
    LocalDate validade,

    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer quantidade,

    @NotNull(message = "deve ser informado")
    Long camaraId
) {}
