package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "PoliticaTermica", description = "Uma versão da política térmica. Versões nunca mudam depois de "
    + "gravadas; a vigente é a de maior `versao`.")
public record PoliticaTermicaResponse(
    @Schema(description = "Câmara dona da política.", examples = "1")
    long camaraId,
    @Schema(description = "Número da versão, a partir de 1. Envie-o como `versaoEsperada` para alterar.",
        examples = "2")
    int versao,
    @Schema(description = "Temperatura mínima em °C.", examples = "2.0")
    BigDecimal temperaturaMinima,
    @Schema(description = "Temperatura máxima em °C.", examples = "8.0")
    BigDecimal temperaturaMaxima,
    @Schema(description = "Leituras inseguras consecutivas que abrem uma ocorrência.", examples = "3")
    int limiar,
    @Schema(description = "UUID do administrador que criou a versão. Nulo quando não há autor conhecido, como na "
        + "versão 1 criada no cadastro ou na migração.", nullable = true)
    UUID autorId,
    @Schema(examples = "2026-11-22T14:05:00-03:00")
    OffsetDateTime criadaEm
) {
    public static PoliticaTermicaResponse de(long camaraId, PoliticaTermica politica) {
        return new PoliticaTermicaResponse(camaraId, politica.getVersao(), politica.getTemperaturaMinima(),
            politica.getTemperaturaMaxima(), politica.getLimiar(), politica.getAutorId().orElse(null),
            politica.getCriadaEm());
    }
}
