package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.PoliticaDaAnalise;

import java.util.List;
import java.util.Objects;

/**
 * Fotografia da câmara usada numa análise (ADR 0003, seção 5): política vigente, última leitura aceita, início do
 * episódio atual e contexto da janela. A confirmação só grava o resultado se a câmara ainda estiver neste mesmo
 * ponto.
 *
 * @param ultimaLeituraId   última leitura aceita da câmara, de qualquer política; nulo se não houver
 * @param inicioDoEpisodio  leitura que encerrou a última ocorrência resolvida; o contexto só usa leituras posteriores
 *                          a ela; nulo se nunca houve resolução
 * @param contexto          até N−1 leituras aceitas da política vigente, em ordem cronológica
 */
record SnapshotDaCamara(long camaraId, PoliticaDaAnalise politica, Long ultimaLeituraId, Long inicioDoEpisodio,
                        List<Leitura> contexto) {

    SnapshotDaCamara {
        Objects.requireNonNull(politica, "politica");
        contexto = List.copyOf(Objects.requireNonNull(contexto, "contexto"));
    }

    JanelaDeAnalise janela(List<NovaLeitura> candidatas) {
        return new JanelaDeAnalise(camaraId, politica, contexto.stream().map(LeituraDaAnalise::de).toList(),
                candidatas.stream().map(NovaLeitura::paraAnalise).toList());
    }

    /** Mesmo ponto da câmara: nenhuma leitura nova, mesma política e nenhuma resolução no meio. */
    boolean mesmoPontoQue(SnapshotDaCamara outro) {
        return camaraId == outro.camaraId
                && politica.equals(outro.politica)
                && Objects.equals(ultimaLeituraId, outro.ultimaLeituraId)
                && Objects.equals(inicioDoEpisodio, outro.inicioDoEpisodio);
    }
}
