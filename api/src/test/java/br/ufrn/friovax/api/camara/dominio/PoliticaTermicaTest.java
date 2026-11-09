package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaTermicaTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-07T10:00:00-03:00");

    @Test
    void versaoInicialTemLimiarTresENormalizaATemperatura() {
        var politica = PoliticaTermica.inicial(new BigDecimal("2"), new BigDecimal("8.00"), null, AGORA);

        assertEquals(1, politica.getVersao());
        assertEquals(PoliticaTermica.LIMIAR_INICIAL, politica.getLimiar());
        assertEquals(new BigDecimal("2.0"), politica.getTemperaturaMinima());
        assertEquals(new BigDecimal("8.0"), politica.getTemperaturaMaxima());
        assertTrue(politica.getAutorId().isEmpty());
        assertEquals(AGORA, politica.getCriadaEm());
    }

    @Test
    void recusaFaixaInvertidaOuIgual() {
        assertThrows(ValidacaoDeNegocio.class,
                () -> PoliticaTermica.inicial(new BigDecimal("8.0"), new BigDecimal("2.0"), null, AGORA));
        assertThrows(ValidacaoDeNegocio.class,
                () -> PoliticaTermica.inicial(new BigDecimal("5.0"), new BigDecimal("5.0"), null, AGORA));
    }

    @Test
    void recusaMaisDeUmaCasaDecimal() {
        var erro = assertThrows(ValidacaoDeNegocio.class,
                () -> PoliticaTermica.inicial(new BigDecimal("2.05"), new BigDecimal("8.0"), null, AGORA));
        assertEquals("temperaturaMinima", erro.campo());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void recusaLimiarForaDeUmACem(int limiar) {
        var inicial = PoliticaTermica.inicial(new BigDecimal("2.0"), new BigDecimal("8.0"), null, AGORA);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> inicial.proximaVersao(
                new BigDecimal("2.0"), new BigDecimal("8.0"), limiar, null, AGORA));
        assertEquals("limiar", erro.campo());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void aceitaOsLimitesDoLimiar(int limiar) {
        var inicial = PoliticaTermica.inicial(new BigDecimal("2.0"), new BigDecimal("8.0"), null, AGORA);

        assertDoesNotThrow(() -> inicial.proximaVersao(
                new BigDecimal("2.0"), new BigDecimal("8.0"), limiar, null, AGORA));
    }

    @Test
    void proximaVersaoIncrementaERegistraAutorEInstante() {
        var autor = UUID.randomUUID();
        var inicial = PoliticaTermica.inicial(new BigDecimal("2.0"), new BigDecimal("8.0"), null, AGORA);

        var segunda = inicial.proximaVersao(new BigDecimal("3.0"), new BigDecimal("7.0"), 5, autor,
                AGORA.plusHours(1));

        assertEquals(2, segunda.getVersao());
        assertEquals(new BigDecimal("3.0"), segunda.getTemperaturaMinima());
        assertEquals(5, segunda.getLimiar());
        assertEquals(autor, segunda.getAutorId().orElseThrow());
        assertEquals(AGORA.plusHours(1), segunda.getCriadaEm());
        assertEquals(1, inicial.getVersao(), "a versão anterior não muda");
    }

    @Test
    void comparaValoresIgnorandoAEscala() {
        var politica = PoliticaTermica.inicial(new BigDecimal("2.0"), new BigDecimal("8.0"), null, AGORA);

        assertTrue(politica.temMesmosValores(new BigDecimal("2"), new BigDecimal("8.00"), 3));
        assertFalse(politica.temMesmosValores(new BigDecimal("2.0"), new BigDecimal("8.0"), 4));
        assertFalse(politica.temMesmosValores(new BigDecimal("2.1"), new BigDecimal("8.0"), 3));
    }
}
