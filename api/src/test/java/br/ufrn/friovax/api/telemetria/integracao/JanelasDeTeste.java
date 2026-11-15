package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.PoliticaDaAnalise;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Janelas com faixa 2–8 °C e N=3, como nos cenários da ADR 0003. */
final class JanelasDeTeste {

    static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-10T09:00:00-03:00");
    static final PoliticaDaAnalise POLITICA = new PoliticaDaAnalise(2, new BigDecimal("2.0"),
            new BigDecimal("8.0"), 3);

    private JanelasDeTeste() {
    }

    static LeituraDaAnalise leitura(String id, int minutos, String temperatura) {
        return new LeituraDaAnalise(id, INICIO.plusMinutes(minutos), new BigDecimal(temperatura));
    }

    /** Contexto vazio e candidatas c1, c2... com as temperaturas informadas, a cada minuto. */
    static JanelaDeAnalise janela(long camaraId, String... temperaturas) {
        var candidatas = new ArrayList<LeituraDaAnalise>();
        for (int i = 0; i < temperaturas.length; i++) {
            candidatas.add(leitura("c" + (i + 1), i, temperaturas[i]));
        }
        return new JanelaDeAnalise(camaraId, POLITICA, List.of(), candidatas);
    }
}
