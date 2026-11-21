package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;

/** Resultado do passo (a) da recepção: a chave já foi aceita ou há um snapshot pronto para análise. */
sealed interface Preparacao {

    /** Reenvio com conteúdo equivalente: devolve o registro original sem chamar o Go. */
    record Repetida(Leitura leitura) implements Preparacao {
    }

    /** Candidata elegível e em ordem; a análise usa este snapshot. */
    record Pronta(SnapshotDaCamara snapshot) implements Preparacao {
    }
}
