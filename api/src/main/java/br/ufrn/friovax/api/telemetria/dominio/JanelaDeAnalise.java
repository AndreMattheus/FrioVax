package br.ufrn.friovax.api.telemetria.dominio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Janela de uma câmara (ADR 0003, seção 3): política vigente, contexto já aceito (até N−1 leituras) e candidatas
 * novas, ambos em ordem estritamente crescente de instante, sem ids externos repetidos.
 */
public record JanelaDeAnalise(long camaraId, PoliticaDaAnalise politica, List<LeituraDaAnalise> contexto,
                              List<LeituraDaAnalise> candidatas) {

    public JanelaDeAnalise {
        if (camaraId < 1) {
            throw new IllegalArgumentException("O id da câmara deve ser positivo.");
        }
        Objects.requireNonNull(politica, "politica");
        contexto = List.copyOf(Objects.requireNonNull(contexto, "contexto"));
        candidatas = List.copyOf(Objects.requireNonNull(candidatas, "candidatas"));
        if (candidatas.isEmpty()) {
            throw new IllegalArgumentException("A janela da câmara " + camaraId + " deve ter ao menos uma candidata.");
        }
        if (contexto.size() > politica.limiar() - 1) {
            throw new IllegalArgumentException("O contexto da câmara " + camaraId + " deve ter no máximo N-1 = "
                    + (politica.limiar() - 1) + " leituras.");
        }
        validarOrdemEUnicidade(camaraId, contexto, candidatas);
    }

    private static void validarOrdemEUnicidade(long camaraId, List<LeituraDaAnalise> contexto,
                                               List<LeituraDaAnalise> candidatas) {
        var ids = new HashSet<String>();
        LeituraDaAnalise anterior = null;
        for (var leitura : concatenadas(contexto, candidatas)) {
            if (!ids.add(leitura.idExterno())) {
                throw new IllegalArgumentException("A janela da câmara " + camaraId + " repete o id externo "
                        + leitura.idExterno() + ".");
            }
            if (anterior != null && !leitura.instante().isAfter(anterior.instante())) {
                throw new IllegalArgumentException("As leituras da câmara " + camaraId
                        + " devem estar em ordem estritamente crescente de instante.");
            }
            anterior = leitura;
        }
    }

    private static List<LeituraDaAnalise> concatenadas(List<LeituraDaAnalise> contexto,
                                                       List<LeituraDaAnalise> candidatas) {
        var todas = new ArrayList<LeituraDaAnalise>(contexto.size() + candidatas.size());
        todas.addAll(contexto);
        todas.addAll(candidatas);
        return todas;
    }
}
