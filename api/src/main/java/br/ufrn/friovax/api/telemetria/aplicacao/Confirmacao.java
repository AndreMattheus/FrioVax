package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;

/** Resultado do passo (c) da recepção. */
sealed interface Confirmacao {

    /** Primeira aceitação: a leitura foi gravada com o resultado calculado para o snapshot confirmado. */
    record Aceita(Leitura leitura, Long ocorrenciaAbertaId) implements Confirmacao {
    }

    /** Outra requisição aceitou a mesma chave, com conteúdo equivalente, durante a análise. */
    record Repetida(Leitura leitura, Long ocorrenciaAbertaId) implements Confirmacao {
    }

    /** A câmara saiu do ponto analisado; o resultado foi descartado sem gravar nada. */
    record SnapshotAlterado() implements Confirmacao {
    }
}
