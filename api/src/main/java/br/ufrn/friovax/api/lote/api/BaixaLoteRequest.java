package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import jakarta.validation.constraints.NotNull;

//Corpo do POST /api/lotes/{id}/baixas
// intervalo da quantidade (1 até a quantidade atual) é validado pelo domínio, que conhece o saldo do lote.

 public record BaixaLoteRequest(
    @NotNull(message = "deve ser informada") Integer quantidade,
    @NotNull(message = "deve ser informado") MotivoBaixa motivo
) {}
