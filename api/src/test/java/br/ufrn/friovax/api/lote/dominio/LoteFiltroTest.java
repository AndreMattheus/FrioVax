package br.ufrn.friovax.api.lote.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoteFiltroTest {

    private static final LocalDate DATA = LocalDate.of(2027, 1, 1);

    @Test
    void deveAceitarIntervaloInclusivoOuAberto() {
        assertDoesNotThrow(() -> new LoteFiltro(null, DATA, DATA, null, null, true));
        assertDoesNotThrow(() -> new LoteFiltro(null, DATA, null, null, null, true));
        assertDoesNotThrow(() -> new LoteFiltro(null, null, DATA, null, null, true));
    }

    @Test
    void deveRejeitarIntervaloDeValidadeInvertido() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoteFiltro(null, DATA.plusDays(1), DATA, null, null, true));
    }

    @Test
    void deveTratarTextoVazioComoAusente() {
        assertNull(new LoteFiltro("   ", null, null, null, null, true).imunobiologico());
    }
}
