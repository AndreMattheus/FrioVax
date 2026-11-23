package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;

/** Desfecho de uma entrada do lote. O ID da ocorrência pertence à leitura que a abriu. */
public record ResultadoDoLote(NovaLeitura entrada, Desfecho desfecho, Leitura leitura, Long ocorrenciaId) {

    static ResultadoDoLote rejeitada(NovaLeitura entrada, Desfecho motivo) {
        return new ResultadoDoLote(entrada, motivo, null, null);
    }

    public enum Desfecho {
        ACEITA,
        REPETIDA,
        CAMARA_INEXISTENTE,
        CAMARA_INAPTA,
        CHAVE_CONFLITANTE,
        FORA_DE_ORDEM,
        SNAPSHOT_ALTERADO,
        ANALISE_REJEITADA,
        FALHA_TRANSACIONAL
    }
}
