package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

// O código é imutável. Zero só é aceito para manter a quantidade de um lote já esgotado.
@Schema(description = "Substitui todos os campos editáveis do lote. O `codigo` é imutável e, se enviado, é "
    + "ignorado.")
public record AtualizarLoteRequest(
    @Schema(description = "Nome do imunobiológico, de 1 a 100 caracteres após o `trim`.",
        examples = "Febre amarela")
    @NotBlank(message = "deve ser informado") String imunobiologico,
    @Schema(description = "Fabricante, de 1 a 100 caracteres após o `trim`.", examples = "Bio-Manguinhos")
    @NotBlank(message = "deve ser informado") String fabricante,
    @Schema(description = "Data de validade. Uma data diferente da armazenada deve ser posterior a hoje em "
        + "`America/Fortaleza`; repetir a data armazenada é aceito mesmo que o lote já tenha vencido.",
        examples = "2027-03-31")
    @NotNull(message = "deve ser informada") LocalDate validade,
    @Schema(description = "Quantidade em doses. Pode aumentar ou permanecer igual; reduções retornam 422 e "
        + "passam pela baixa com motivo. Um lote `ESGOTADO` pode manter `0`, mas não receber novas doses (409).",
        examples = "1500")
    @NotNull(message = "deve ser informada")
    @PositiveOrZero(message = "não pode ser negativa") Integer quantidade,
    @Schema(description = "Câmara do lote. Trocar de câmara move a quantidade da origem para o destino, que deve "
        + "existir (404), estar ativa, `OPERACIONAL` e ter capacidade livre (409).", examples = "2")
    @NotNull(message = "deve ser informado") Long camaraId
) {}
