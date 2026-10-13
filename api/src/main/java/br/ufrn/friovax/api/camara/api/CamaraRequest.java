package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Dados para cadastrar uma câmara. A câmara é criada com `ativo=true` e `ocupacao=0`.")
@TemperaturaValida
public record CamaraRequest(

    @Schema(description = "Código de negócio, imutável após o cadastro. Recebe `trim` e é convertido para "
        + "maiúsculas; depois disso deve ter de 3 a 20 caracteres entre `A-Z`, `0-9` e `-`. Único entre todas as "
        + "câmaras, inclusive inativas.", examples = "cam-01")
    @NotBlank(message = "deve ser informado")
    String codigo,

    @Schema(description = "Nome da câmara, de 1 a 100 caracteres após o `trim`.",
        examples = "Câmara fria principal")
    @NotBlank(message = "deve ser informado")
    String nome,

    @Schema(description = "Unidade de saúde onde a câmara está, de 1 a 100 caracteres após o `trim`.",
        examples = "UBS Centro")
    @NotBlank(message = "deve ser informada")
    String unidade,

    @Schema(description = "Capacidade em doses. Inteiro `int32`; número com notação decimal, como `10.0`, "
        + "retorna 400.", examples = "5000")
    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer capacidade,

    @Schema(description = "Temperatura mínima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal "
        + "(zeros à direita são desconsiderados). Deve ser menor que `temperaturaMaxima`.", examples = "2.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMinima,

    @Schema(description = "Temperatura máxima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal.",
        examples = "8.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMaxima,

    @Schema(description = "Estado operacional inicial. Somente `OPERACIONAL` aceita lotes.")
    @NotNull(message = "deve ser informado")
    EstadoCamara estado
) {}
