package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcorrenciaTest {
    private static final OffsetDateTime ABERTURA = OffsetDateTime.parse("2026-11-10T12:00:00-03:00");
    private static final List<LoteAfetado> LOTES = List.of(
            new LoteAfetado(10, "FX-01", "Febre amarela", 100),
            new LoteAfetado(11, "HB-02", "Hepatite B", 40));

    @Test
    void abreComEvidenciasEFotografiaDosLotes() {
        var ocorrencia = abrir(List.of(1L, 2L, 3L), LOTES, false);

        assertTrue(ocorrencia.isAberta());
        assertEquals(3, ocorrencia.getLeituraAberturaId());
        assertEquals(List.of(1L, 2L, 3L), ocorrencia.getEvidencias());
        assertEquals(LOTES, ocorrencia.getLotesAfetados());
        assertTrue(ocorrencia.getResolvidaEm().isEmpty());
    }

    @Test
    void naoAbreSegundaOcorrenciaNaMesmaCamara() {
        assertThrows(OcorrenciaJaAberta.class, () -> abrir(List.of(1L, 2L, 3L), LOTES, true));
    }

    @Test
    void recusaEvidenciasSemALeituraDeAberturaOuRepetidas() {
        assertThrows(IllegalArgumentException.class, () -> abrir(List.of(1L, 2L), LOTES, false));
        assertThrows(IllegalArgumentException.class, () -> abrir(List.of(), LOTES, false));
        assertThrows(IllegalArgumentException.class, () -> abrir(List.of(2L, 2L, 3L), LOTES, false));
    }

    @Test
    void recusaLoteRepetidoNaMesmaOcorrencia() {
        var repetidos = List.of(new LoteAfetado(10, "FX-01", "Febre amarela", 100),
                new LoteAfetado(10, "FX-01", "Febre amarela", 100));

        assertThrows(IllegalArgumentException.class, () -> abrir(List.of(3L), repetidos, false));
    }

    @Test
    void fotografiaNaoMudaSeAListaOriginalMudar() {
        var lotes = new ArrayList<>(LOTES);
        var ocorrencia = abrir(List.of(3L), lotes, false);

        lotes.clear();

        assertEquals(2, ocorrencia.getLotesAfetados().size());
        assertThrows(UnsupportedOperationException.class, () -> ocorrencia.getLotesAfetados().clear());
    }

    @Test
    void resolveComJustificativaEMarcaOFimDoEpisodio() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);
        var admin = UUID.randomUUID();

        ocorrencia.resolver("  Câmara verificada; lotes avaliados  ", 7, admin, ABERTURA.plusHours(2));

        assertFalse(ocorrencia.isAberta());
        assertEquals("Câmara verificada; lotes avaliados", ocorrencia.getJustificativa().orElseThrow());
        assertEquals(7L, ocorrencia.getLeituraResolucaoId().orElseThrow());
        assertEquals(admin, ocorrencia.getResolvidaPor().orElseThrow());
    }

    @Test
    void naoResolveSemJustificativaNemDuasVezes() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);

        var erro = assertThrows(ValidacaoDeNegocio.class,
                () -> ocorrencia.resolver("   ", 7, null, ABERTURA.plusHours(1)));
        assertEquals("justificativa", erro.campo());
        assertTrue(ocorrencia.isAberta(), "a tentativa recusada não muda nada");

        ocorrencia.resolver("ok", 7, null, ABERTURA.plusHours(1));
        assertThrows(EstadoIncompativel.class, () -> ocorrencia.resolver("de novo", 8, null, ABERTURA.plusHours(2)));
    }

    private static Ocorrencia abrir(List<Long> evidencias, List<LoteAfetado> lotes, boolean existeAberta) {
        return Ocorrencia.abrir(1, 1, 3, evidencias, lotes, existeAberta, ABERTURA);
    }
}
