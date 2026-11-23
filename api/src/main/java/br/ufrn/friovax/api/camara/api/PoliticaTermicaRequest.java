package br.ufrn.friovax.api.camara.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Nova faixa e limiar da política térmica, sobre a versão que o cliente leu. Autor e data vêm "
    + "do servidor; campos como `autorId` ou `versao`, se enviados, são ignorados.")
public record PoliticaTermicaRequest(

    @Schema(description = "Versão vigente que o cliente consultou (`versao` do `GET`). Se outra alteração chegou "
        + "antes, a resposta é 409 e nada é gravado.", examples = "1")
    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer versaoEsperada,

    @Schema(description = "Temperatura mínima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal. Deve ser "
        + "menor que `temperaturaMaxima`.", examples = "2.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMinima,

    @Schema(description = "Temperatura máxima em °C, de -999.9 a 999.9, com no máximo 1 casa decimal.",
        examples = "8.0")
    @NotNull(message = "deve ser informada")
    BigDecimal temperaturaMaxima,

    @Schema(description = "Leituras inseguras consecutivas que abrem uma ocorrência, de 1 a 100.", examples = "3")
    @NotNull(message = "deve ser informado")
    Integer limiar
) {}
