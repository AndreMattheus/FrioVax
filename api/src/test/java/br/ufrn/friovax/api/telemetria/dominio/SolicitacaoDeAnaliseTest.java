package br.ufrn.friovax.api.telemetria.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolicitacaoDeAnaliseTest {

    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-10T09:00:00-03:00");
    private static final PoliticaDaAnalise POLITICA =
            new PoliticaDaAnalise(1, new BigDecimal("2.0"), new BigDecimal("8.0"), 3);

    @Test
    void leituraNormalizaParaUtcEEscalaUmSemArredondar() {
        var leitura = new LeituraDaAnalise("r1", OffsetDateTime.parse("2026-11-10T09:00:00.123456-03:00"),
                new BigDecimal("8.70"));

        assertEquals(ZoneOffset.UTC, leitura.instante().getOffset());
        assertEquals(12, leitura.instante().getHour());
        assertEquals(new BigDecimal("8.7"), leitura.temperatura());
    }

    @Test
    void leituraRecusaPrecisaoQueNaoCabeEmDecimosOuMicrossegundos() {
        assertThrows(IllegalArgumentException.class, () -> leitura("r1", 0, "8.75"));
        assertThrows(IllegalArgumentException.class, () -> leitura("r1", 0, "1000.0"));
        assertThrows(IllegalArgumentException.class,
                () -> new LeituraDaAnalise("r1", INICIO.plusNanos(1), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> leitura(" ", 0, "5"));
    }

    @Test
    void politicaExigeFaixaCrescenteELimiarDeUmACem() {
        assertThrows(IllegalArgumentException.class,
                () -> new PoliticaDaAnalise(1, new BigDecimal("8.0"), new BigDecimal("8.0"), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new PoliticaDaAnalise(1, new BigDecimal("2.0"), new BigDecimal("8.0"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PoliticaDaAnalise(1, new BigDecimal("2.0"), new BigDecimal("8.0"), 101));
        assertThrows(IllegalArgumentException.class,
                () -> new PoliticaDaAnalise(0, new BigDecimal("2.0"), new BigDecimal("8.0"), 3));
    }

    @Test
    void janelaExigeCandidataContextoAteNMenosUmEOrdemCrescente() {
        var candidata = List.of(leitura("c1", 5, "9"));

        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, POLITICA, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, POLITICA,
                List.of(leitura("r1", 0, "9"), leitura("r2", 1, "9"), leitura("r3", 2, "9")), candidata));
        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, POLITICA,
                List.of(leitura("r1", 9, "9")), candidata));
        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, POLITICA,
                List.of(leitura("c1", 0, "9")), candidata));
        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, POLITICA, List.of(),
                List.of(leitura("c1", 5, "9"), leitura("c2", 5, "9"))));
        assertDoesNotThrow(() -> new JanelaDeAnalise(1, POLITICA,
                List.of(leitura("r1", 0, "9"), leitura("r2", 1, "9")), candidata));
    }

    @Test
    void limiarUmDispensaContexto() {
        var politica = new PoliticaDaAnalise(1, new BigDecimal("2.0"), new BigDecimal("8.0"), 1);

        assertThrows(IllegalArgumentException.class, () -> new JanelaDeAnalise(1, politica,
                List.of(leitura("r1", 0, "9")), List.of(leitura("c1", 1, "9"))));
    }

    @Test
    void solicitacaoTemCorrelacaoUmaJanelaPorCamaraEAteCinquentaCandidatas() {
        assertThrows(IllegalArgumentException.class, () -> SolicitacaoDeAnalise.de(" ", janela(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new SolicitacaoDeAnalise("corr", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitacaoDeAnalise("corr", List.of(janela(1, 1), janela(1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> new SolicitacaoDeAnalise("corr", List.of(janela(1, 30), janela(2, 21))));
        assertDoesNotThrow(() -> new SolicitacaoDeAnalise("corr", List.of(janela(1, 30), janela(2, 20))));
    }

    @Test
    void correlacaoSoAceitaAsciiSeguroParaMetadadoELog() {
        assertDoesNotThrow(() -> SolicitacaoDeAnalise.de("lote-2026.11:abc_1", janela(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> SolicitacaoDeAnalise.de("análise-1", janela(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> SolicitacaoDeAnalise.de("a\nb", janela(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> SolicitacaoDeAnalise.de("com espaço", janela(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> SolicitacaoDeAnalise.de("x".repeat(SolicitacaoDeAnalise.CORRELACAO_MAXIMA + 1), janela(1, 1)));
    }

    @Test
    void resultadoInseguroTerminaNaCandidataComUmaEvidenciaPorPasso() {
        assertDoesNotThrow(() -> new ResultadoDaCandidata("c3", ResultadoTermico.insegura(3, true),
                List.of("c1", "c2", "c3")));
        assertThrows(IllegalArgumentException.class, () -> new ResultadoDaCandidata("c3",
                ResultadoTermico.insegura(2, false), List.of("c1", "c2")));
        assertThrows(IllegalArgumentException.class, () -> new ResultadoDaCandidata("c4",
                ResultadoTermico.segura(), List.of("c3")));
    }

    private static LeituraDaAnalise leitura(String id, int minutos, String temperatura) {
        return new LeituraDaAnalise(id, INICIO.plusMinutes(minutos), new BigDecimal(temperatura));
    }

    private static JanelaDeAnalise janela(long camaraId, int candidatas) {
        var leituras = new ArrayList<LeituraDaAnalise>();
        IntStream.range(0, candidatas).forEach(i -> leituras.add(leitura("c" + i, i, "5")));
        return new JanelaDeAnalise(camaraId, POLITICA, List.of(), leituras);
    }
}
