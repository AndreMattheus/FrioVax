package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.ConsultarCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Representação de uma câmara.")
public record CamaraResponse(
        @Schema(description = "Identificador técnico usado nas rotas.", examples = "1")
        long id,
        @Schema(description = "Código de negócio normalizado em maiúsculas.", examples = "CAM-01")
        String codigo,
        @Schema(examples = "Câmara fria principal")
        String nome,
        @Schema(examples = "UBS Centro")
        String unidade,
        @Schema(description = "Capacidade em doses.", examples = "5000")
        int capacidade,
        @Schema(description = "Temperatura mínima em °C.", examples = "2.0")
        BigDecimal temperaturaMinima,
        @Schema(description = "Temperatura máxima em °C.", examples = "8.0")
        BigDecimal temperaturaMaxima,
        EstadoCamara estado,
        @Schema(description = "Soma da `quantidade` dos lotes ativos da câmara, em doses.", examples = "1200")
        int ocupacao,
        @Schema(description = "`false` depois da inativação por `DELETE`.")
        boolean ativo,
        @Schema(examples = "2026-09-23T14:05:00-03:00")
        OffsetDateTime criadoEm,
        @Schema(examples = "2026-09-23T14:05:00-03:00")
        OffsetDateTime atualizadoEm
) {
    public static CamaraResponse de(ConsultarCamara.Resultado resultado) {
        var c = resultado.camara();
        return new CamaraResponse(c.getId(), c.getCodigo(), c.getNome(), c.getUnidade(), c.getCapacidade(),
                c.getTemperaturaMinima(), c.getTemperaturaMaxima(), c.getEstado(),
                Math.toIntExact(resultado.ocupacao()),
                c.isAtivo(), c.getCriadoEm(), c.getAtualizadoEm());
    }
}
