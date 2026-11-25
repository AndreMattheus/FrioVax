package br.ufrn.friovax.api.telemetria.api;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Resolução de ocorrência. Autor, instante e leitura de resolução vêm do servidor; campos como "
    + "`resolvidaPor` ou `resolvidaEm`, se enviados, são ignorados.")
public record ResolucaoRequest(

    @Schema(description = "Tratamento dado à excursão, de 1 a 500 caracteres após o `trim`. Reenviar a mesma "
        + "justificativa recupera a resolução original.", maxLength = 500,
        examples = "Porta reparada; temperatura estabilizada e lotes avaliados pela vigilância")
    @NotBlank(message = "deve ser informada")
    String justificativa) {
}
