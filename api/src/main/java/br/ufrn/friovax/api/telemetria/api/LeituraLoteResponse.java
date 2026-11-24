package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.aplicacao.ResultadoDoLote;
import br.ufrn.friovax.api.telemetria.aplicacao.ResultadoDoLote.Desfecho;
import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Resultado de uma entrada na posição original do lote.")
public record LeituraLoteResponse(
        long camaraId,
        String idExterno,
        Situacao situacao,
        @Schema(description = "201 aceita, 200 repetida, 404 câmara inexistente, 409 conflito, "
                + "502 janela rejeitada pelo serviço de análise ou 500 falha transacional.", examples = "201")
        int codigo,
        @Schema(description = "ID da leitura persistida, quando aceita ou repetida.", nullable = true)
        Long id,
        @Schema(nullable = true)
        ClassificacaoTermica classificacao,
        @Schema(nullable = true)
        Integer sequencia,
        @Schema(nullable = true)
        Boolean limiarAtingido,
        @Schema(description = "ID da ocorrência aberta por esta leitura, inclusive em reenvio.", nullable = true)
        Long ocorrenciaId,
        @Schema(description = "Motivo de rejeição: CAMARA_INEXISTENTE, CAMARA_INAPTA, CHAVE_CONFLITANTE, FORA_DE_ORDEM, "
                + "SNAPSHOT_ALTERADO, ANALISE_REJEITADA ou FALHA_TRANSACIONAL.", examples = "SNAPSHOT_ALTERADO",
                nullable = true)
        String motivo
) {
    static LeituraLoteResponse de(ResultadoDoLote resposta) {
        var entrada = resposta.entrada();
        var leitura = resposta.leitura();
        var desfecho = resposta.desfecho();
        if (leitura == null) {
            return new LeituraLoteResponse(entrada.camaraId(), entrada.idExterno(), Situacao.REJEITADA,
                    codigo(desfecho), null, null, null, null, null, desfecho.name());
        }
        var situacao = desfecho == Desfecho.ACEITA ? Situacao.ACEITA : Situacao.REPETIDA;
        var termico = leitura.getResultado();
        return new LeituraLoteResponse(entrada.camaraId(), entrada.idExterno(), situacao, codigo(desfecho),
                leitura.getId(), termico.classificacao(), termico.sequencia(), termico.limiarAtingido(),
                resposta.ocorrenciaId(), null);
    }

    private static int codigo(Desfecho desfecho) {
        return switch (desfecho) {
            case ACEITA -> 201;
            case REPETIDA -> 200;
            case CAMARA_INEXISTENTE -> 404;
            case CAMARA_INAPTA, CHAVE_CONFLITANTE, FORA_DE_ORDEM, SNAPSHOT_ALTERADO -> 409;
            case ANALISE_REJEITADA -> 502;
            case FALHA_TRANSACIONAL -> 500;
        };
    }

    public enum Situacao { ACEITA, REPETIDA, REJEITADA }
}
