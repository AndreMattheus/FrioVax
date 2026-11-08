package br.ufrn.friovax.api.telemetria.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Leitura enviada para análise: contexto já aceito ou candidata nova. A câmara e a versão da política são as da
 * janela. Temperatura com no máximo uma casa decimal e instante em UTC com precisão de microssegundos, para que a
 * conversão em décimos e em {@code Timestamp} seja exata.
 */
public record LeituraDaAnalise(String idExterno, OffsetDateTime instante, BigDecimal temperatura) {

    private static final BigDecimal TEMPERATURA_MINIMA = new BigDecimal("-999.9");
    private static final BigDecimal TEMPERATURA_MAXIMA = new BigDecimal("999.9");
    private static final int NANOS_POR_MICROSSEGUNDO = 1_000;

    public LeituraDaAnalise {
        if (idExterno == null || idExterno.isBlank()) {
            throw new IllegalArgumentException("O id externo da leitura deve ser informado.");
        }
        Objects.requireNonNull(instante, "instante");
        if (instante.getNano() % NANOS_POR_MICROSSEGUNDO != 0) {
            throw new IllegalArgumentException("O instante deve ter precisão de até microssegundos.");
        }
        instante = instante.withOffsetSameInstant(ZoneOffset.UTC);
        temperatura = temperaturaEmEscalaUm(temperatura, "temperatura");
    }

    /** Contexto ou candidata a partir de uma leitura já aceita, sem alterar seus valores. */
    public static LeituraDaAnalise de(Leitura leitura) {
        Objects.requireNonNull(leitura, "leitura");
        return new LeituraDaAnalise(leitura.getIdExterno(), leitura.getInstante(), leitura.getTemperatura());
    }

    static BigDecimal temperaturaEmEscalaUm(BigDecimal temperatura, String campo) {
        Objects.requireNonNull(temperatura, campo);
        if (temperatura.compareTo(TEMPERATURA_MINIMA) < 0 || temperatura.compareTo(TEMPERATURA_MAXIMA) > 0) {
            throw new IllegalArgumentException("O campo " + campo + " deve estar entre -999.9 e 999.9.");
        }
        var normalizada = temperatura.stripTrailingZeros();
        if (normalizada.scale() > 1) {
            throw new IllegalArgumentException("O campo " + campo + " deve ter no máximo 1 casa decimal.");
        }
        return normalizada.setScale(1);
    }
}
