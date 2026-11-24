package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Tamanhos, escala da temperatura, offset e tolerância futura são verificados no domínio (ADR 0003, seção 5).
@Schema(description = "Leitura de temperatura de uma câmara. A chave de idempotência é `(camaraId, idExterno)`.")
public record LeituraRequest(

    @Schema(description = "Identificador da leitura na origem, de 1 a 100 caracteres, sem caractere nulo. Não recebe `trim` nem "
        + "conversão de maiúsculas: `S-1` e `s-1` são chaves diferentes. Reenviar a mesma chave com o mesmo "
        + "conteúdo devolve o registro original (200).", examples = "sensor-7:000123")
    @NotBlank(message = "deve ser informado")
    String idExterno,

    @Schema(description = "ID positivo da câmara medida. A câmara deve existir (404), estar ativa e "
        + "`OPERACIONAL` (409).", minimum = "1", examples = "1")
    @NotNull(message = "deve ser informado")
    Long camaraId,

    @Schema(description = "Temperatura em °C, de -999.9 a 999.9, com no máximo 1 casa decimal (zeros à direita "
        + "são desconsiderados: `8.50` equivale a `8.5`).", examples = "9.4")
    @NotNull(message = "deve ser informada")
    BigDecimal temperatura,

    @Schema(description = "Instante da medição em ISO 8601 com offset obrigatório (`Z` ou `±hh:mm`) e no máximo 6 "
        + "casas nos segundos. Deve ser posterior à última leitura aceita da câmara (409) e no máximo 5 minutos à "
        + "frente do relógio da API (422). Em UTC, deve estar entre os anos 0001 e 9999.",
        type = SchemaType.STRING, format = "date-time", examples = "2026-11-20T10:15:00-03:00")
    @NotBlank(message = "deve ser informado")
    String medidaEm,

    @Schema(description = "Origem da leitura, como o sensor ou o operador que a registrou; de 1 a 100 caracteres "
        + "após o `trim`, sem caractere nulo.", examples = "sensor-7")
    @NotBlank(message = "deve ser informada")
    String origem
) {
    /** Exige offset explícito: um instante local seria ambíguo (ADR 0003, seção 5). */
    OffsetDateTime instanteDaMedicao() {
        try {
            return OffsetDateTime.parse(medidaEm.strip(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException semOffset) {
            throw new ValidacaoDeNegocio(NovaLeitura.CAMPO_MEDIDA,
                "deve ser um instante ISO 8601 com offset, como 2026-11-20T10:15:00-03:00");
        }
    }
}
