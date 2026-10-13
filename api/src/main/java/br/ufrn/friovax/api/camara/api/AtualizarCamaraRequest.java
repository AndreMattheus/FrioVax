package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

//corpo do PUT /api/camaras/{id}: todos os campos editáveis, exceto codigo, que é imutável
@Schema(description = "Substitui todos os campos editáveis da câmara. O `codigo` é imutável e, se enviado, é "
    + "ignorado.")
public record AtualizarCamaraRequest(

    @Schema(description = "Nome da câmara, de 1 a 100 caracteres após o `trim`.",
        examples = "Câmara fria principal")
    @NotBlank(message = "deve ser informado")
    String nome,

    @Schema(description = "Unidade de saúde onde a câmara está, de 1 a 100 caracteres após o `trim`.",
        examples = "UBS Centro")
    @NotBlank(message = "deve ser informada")
    String unidade,

    @Schema(description = "Capacidade em doses. Não pode ficar abaixo da `ocupacao` atual (409).",
        examples = "6000")
    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer capacidade,

    @Schema(description = "Temperatura mínima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal. Deve ser "
        + "menor que `temperaturaMaxima`.", examples = "2.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMinima,

    @Schema(description = "Temperatura máxima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal.",
        examples = "8.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMaxima,

    @Schema(description = "Novo estado operacional. Passar para `MANUTENCAO` ou `DESATIVADA` exige que a câmara "
        + "não tenha lotes ativos (409).")
    @NotNull(message = "deve ser informado")
    EstadoCamara estado
) {}
