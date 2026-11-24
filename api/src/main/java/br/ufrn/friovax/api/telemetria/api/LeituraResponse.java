package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Leitura aceita, com a versão da política usada e o resultado térmico confirmado.")
public record LeituraResponse(
        @Schema(description = "Identificador técnico da leitura.", examples = "42")
        long id,
        @Schema(examples = "1")
        long camaraId,
        @Schema(description = "Identificador na origem, como enviado.", examples = "sensor-7:000123")
        String idExterno,
        @Schema(description = "Temperatura em °C com 1 casa decimal.", examples = "9.4")
        BigDecimal temperatura,
        @Schema(description = "Instante da medição em UTC.", examples = "2026-11-20T13:15:00Z")
        OffsetDateTime medidaEm,
        @Schema(description = "Origem após o `trim`.", examples = "sensor-7")
        String origem,
        @Schema(description = "Versão da política térmica usada na análise.", examples = "1")
        int politicaVersao,
        @Schema(description = "`SEGURA` dentro da faixa inclusiva da política; `INSEGURA` fora dela.")
        ClassificacaoTermica classificacao,
        @Schema(description = "Leituras inseguras consecutivas até esta, limitada ao limiar N; 0 se segura.",
            examples = "1")
        int sequencia,
        @Schema(description = "`true` quando esta leitura insegura atingiu ou manteve o limiar N.")
        boolean limiarAtingido,
        @Schema(description = "Instante oficial da aceitação em UTC.", examples = "2026-11-20T13:15:02.120Z")
        OffsetDateTime aceitaEm,
        @Schema(description = "Ocorrência aberta por esta leitura ao atingir o limiar sem outra aberta na câmara; "
            + "nulo quando não abriu. O reenvio da mesma chave devolve o mesmo valor.", examples = "7",
            nullable = true)
        Long ocorrenciaAbertaId
) {
    public static LeituraResponse de(Leitura leitura, Long ocorrenciaAbertaId) {
        var resultado = leitura.getResultado();
        return new LeituraResponse(leitura.getId(), leitura.getCamaraId(), leitura.getIdExterno(),
                leitura.getTemperatura(), leitura.getInstante(), leitura.getOrigem(), leitura.getPoliticaVersao(),
                resultado.classificacao(), resultado.sequencia(), resultado.limiarAtingido(), leitura.getAceitaEm(),
                ocorrenciaAbertaId);
    }
}
