package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeituraTest {
    private static final OffsetDateTime ACEITA = OffsetDateTime.parse("2026-11-10T12:00:00-03:00");
    private static final OffsetDateTime MEDIDA = OffsetDateTime.parse("2026-11-10T11:59:00.123456-03:00");

    @Test
    void normalizaInstanteTemperaturaEOrigem() {
        var leitura = nova("L-1", MEDIDA, "8", "  sensor-1  ");

        assertEquals(OffsetDateTime.parse("2026-11-10T14:59:00.123456Z"), leitura.getInstante(),
                "mesmo instante em UTC, sem perder os microssegundos");
        assertEquals(ZoneOffset.UTC, leitura.getInstante().getOffset());
        assertEquals(new BigDecimal("8.0"), leitura.getTemperatura());
        assertEquals("sensor-1", leitura.getOrigem());
    }

    @Test
    void aceitaAte6CasasDecimaisSemArredondar() {
        var comZerosAMais = OffsetDateTime.parse("2026-11-10T12:00:00.123456000Z");
        var semFracao = OffsetDateTime.parse("2026-11-10T12:00:00Z");

        assertEquals(123_456_000, nova("L-1", comZerosAMais, "8.0", "s").getInstante().getNano());
        assertEquals(0, nova("L-2", semFracao, "8.0", "s").getInstante().getNano());
    }

    @Test
    void recusaInstanteComPrecisaoMaiorQueMicrossegundosEmVezDeTruncar() {
        // Truncados, os dois virariam 12:00:00.123456 e seriam tratados como o mesmo instante.
        var a = OffsetDateTime.parse("2026-11-10T12:00:00.123456100Z");
        var b = OffsetDateTime.parse("2026-11-10T12:00:00.123456900Z");

        assertEquals("instante", campo(() -> nova("L-1", a, "8.0", "s")));
        assertEquals("instante", campo(() -> nova("L-1", b, "8.0", "s")));
        assertEquals("instante", campo(() -> nova("L-1", MEDIDA.plusNanos(1), "8.0", "s")));
    }

    @Test
    void reenvioComInstanteDeMaisDe6CasasNaoEhConsideradoIgual() {
        var leitura = nova("L-1", OffsetDateTime.parse("2026-11-10T12:00:00.123456Z"), "8.0", "s");

        assertEquals("instante", campo(() -> leitura.temMesmoConteudo(new BigDecimal("8.0"),
                OffsetDateTime.parse("2026-11-10T12:00:00.123456100Z"), "s")));
    }

    @Test
    void naoNormalizaOIdExterno() {
        assertEquals(" L-1 ", nova(" L-1 ", MEDIDA, "8.0", "s").getIdExterno());
    }

    @Test
    void recusaEntradasInvalidas() {
        assertEquals("idExterno", campo(() -> nova("  ", MEDIDA, "8.0", "s")));
        assertEquals("idExterno", campo(() -> nova("x".repeat(101), MEDIDA, "8.0", "s")));
        assertEquals("instante", campo(() -> nova("L-1", null, "8.0", "s")));
        assertEquals("temperatura", campo(() -> nova("L-1", MEDIDA, "8.05", "s")));
        assertEquals("temperatura", campo(() -> nova("L-1", MEDIDA, "1000.0", "s")));
        assertEquals("origem", campo(() -> nova("L-1", MEDIDA, "8.0", "   ")));
    }

    @Test
    void comparaConteudoPorDecimosInstanteUtcEOrigemSemEspacos() {
        var leitura = nova("L-1", MEDIDA, "8.0", "sensor-1");

        assertTrue(leitura.temMesmoConteudo(new BigDecimal("8"),
                OffsetDateTime.parse("2026-11-10T14:59:00.123456Z"), " sensor-1 "));
        assertFalse(leitura.temMesmoConteudo(new BigDecimal("8.1"), MEDIDA, "sensor-1"));
        assertFalse(leitura.temMesmoConteudo(new BigDecimal("8.0"), MEDIDA.plusSeconds(1), "sensor-1"));
        assertFalse(leitura.temMesmoConteudo(new BigDecimal("8.0"), MEDIDA, "sensor-2"));
    }

    @Test
    void resultadoTermicoCoerente() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoTermico(ClassificacaoTermica.SEGURA, 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoTermico(ClassificacaoTermica.SEGURA, 0, true));
        assertThrows(IllegalArgumentException.class, () -> ResultadoTermico.insegura(0, false));
        assertThrows(IllegalArgumentException.class, () -> ResultadoTermico.insegura(101, true));
        assertEquals(3, ResultadoTermico.insegura(3, true).sequencia());
    }

    private static Leitura nova(String idExterno, OffsetDateTime instante, String temperatura, String origem) {
        return Leitura.nova(1, idExterno, instante, new BigDecimal(temperatura), origem, 1,
                ResultadoTermico.segura(), ACEITA);
    }

    private static String campo(Runnable acao) {
        return assertThrows(ValidacaoDeNegocio.class, acao::run).campo();
    }
}
