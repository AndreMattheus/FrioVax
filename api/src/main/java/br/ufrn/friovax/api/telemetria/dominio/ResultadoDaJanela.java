package br.ufrn.friovax.api.telemetria.dominio;

import java.util.List;
import java.util.Objects;

/** Resultado de uma janela: todas as candidatas analisadas ou a rejeição da janela inteira. */
public sealed interface ResultadoDaJanela {

    long camaraId();

    /** Todas as candidatas da janela, na ordem enviada; nunca apenas a última. */
    record Analisada(long camaraId, int politicaVersao, List<ResultadoDaCandidata> candidatas)
            implements ResultadoDaJanela {

        public Analisada {
            candidatas = List.copyOf(Objects.requireNonNull(candidatas, "candidatas"));
        }
    }

    /**
     * O serviço recusou a janela por uma validação interna. Nenhum resultado térmico se aplica às suas candidatas, e
     * as demais janelas continuam válidas. É falha de integração restrita à câmara, não erro do usuário.
     */
    record Rejeitada(long camaraId, MotivoDaRejeicao motivo, String descricao) implements ResultadoDaJanela {

        public Rejeitada {
            Objects.requireNonNull(motivo, "motivo");
            descricao = descricao == null ? "" : descricao;
        }
    }

    enum MotivoDaRejeicao {
        JANELA_INVALIDA,
        POLITICA_INVALIDA,
        CAMARA_DIVERGENTE,
        VERSAO_DIVERGENTE
    }
}
