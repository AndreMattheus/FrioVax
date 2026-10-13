package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

// O intervalo da quantidade (1 até a quantidade atual) é validado pelo domínio, que conhece o saldo do lote.
@Schema(description = "Dados para registrar a baixa de doses de um lote (contrato, §4.2).")
public record BaixaLoteRequest(

    @Schema(description = "Quantidade de doses retiradas, de 1 até a `quantidade` atual do lote (422).",
        examples = "30")
    @NotNull(message = "deve ser informada")
    Integer quantidade,

    @Schema(description = "Motivo da baixa: `ADMINISTRADA` (aplicada em alguém) ou `PERDA` (quebra, contaminação "
        + "ou outra saída sem aplicação). Valores fora da lista retornam 400.", examples = "ADMINISTRADA")
    @NotNull(message = "deve ser informado")
    MotivoBaixa motivo
) {}
