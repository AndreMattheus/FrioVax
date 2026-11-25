package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    @Test
    void resolveQuandoAUltimaLeituraEhSeguraPosteriorEDaPoliticaVigente() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);
        var admin = UUID.randomUUID();

        ocorrencia.resolver("  Porta reparada  ", segura(5, 1), abertura(), 1, admin, ABERTURA.plusHours(1));

        assertFalse(ocorrencia.isAberta());
        assertEquals(5L, ocorrencia.getLeituraResolucaoId().orElseThrow(), "o próximo episódio começa depois dela");
        assertEquals(admin, ocorrencia.getResolvidaPor().orElseThrow());
        assertEquals(ABERTURA.plusHours(1), ocorrencia.getResolvidaEm().orElseThrow());
    }

    @Test
    void naoResolveSemLeituraDepoisDaAbertura() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);

        assertThrows(ResolucaoNaoPermitida.class, () -> ocorrencia.resolver("ok", abertura(), abertura(), 1,
                UUID.randomUUID(), ABERTURA.plusHours(1)));
        assertTrue(ocorrencia.isAberta());
    }

    @Test
    void naoResolveComUltimaLeituraInseguraMesmoComSeguraAntesDela() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);
        // S-4 segura veio antes; a última é S-5, insegura: só a última conta.
        var ultima = leitura(5, 2, "9.5", ResultadoTermico.insegura(1, false), 1);

        var erro = assertThrows(ResolucaoNaoPermitida.class, () -> ocorrencia.resolver("ok", ultima, abertura(), 1,
                UUID.randomUUID(), ABERTURA.plusHours(1)));
        assertTrue(erro.getMessage().contains("S-5, 9.5 °C) é insegura"), erro.getMessage());
        assertTrue(ocorrencia.isAberta());
    }

    @Test
    void naoResolveComSeguraDeOutraPolitica() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);

        var erro = assertThrows(ResolucaoNaoPermitida.class, () -> ocorrencia.resolver("ok", segura(5, 1),
                abertura(), 2, UUID.randomUUID(), ABERTURA.plusHours(1)));
        assertTrue(erro.getMessage().contains("política v1, e a vigente é a v2"), erro.getMessage());
        assertTrue(ocorrencia.isAberta());
    }

    @Test
    void justificativaEhConferidaAntesDaCondicao() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> ocorrencia.resolver(" ", abertura(), abertura(), 1,
                UUID.randomUUID(), ABERTURA.plusHours(1)));
        assertEquals("justificativa", erro.campo());
        assertThrows(ValidacaoDeNegocio.class, () -> Ocorrencia.justificativaValidada("x".repeat(501)));
        assertEquals("x".repeat(500), Ocorrencia.justificativaValidada("  " + "x".repeat(500) + "  "));
    }

    @Test
    void reconheceReenvioDaMesmaJustificativaDepoisDeResolvida() {
        var ocorrencia = abrir(List.of(3L), LOTES, false);
        assertFalse(ocorrencia.resolvidaCom("Porta reparada"), "aberta não tem resolução a recuperar");

        ocorrencia.resolver("Porta reparada", segura(5, 1), abertura(), 1, UUID.randomUUID(), ABERTURA.plusHours(1));

        assertTrue(ocorrencia.resolvidaCom("  Porta reparada "));
        assertFalse(ocorrencia.resolvidaCom("Porta reparada e câmara limpa"));
        assertFalse(ocorrencia.resolvidaCom(null));
        assertThrows(EstadoIncompativel.class, () -> ocorrencia.resolver("Porta reparada", segura(6, 1),
                abertura(), 1, UUID.randomUUID(), ABERTURA.plusHours(2)), "resolvida não é editada");
    }

    private static Leitura abertura() {
        return leitura(3, 0, "9.0", ResultadoTermico.insegura(3, true), 1);
    }

    private static Leitura segura(long id, int versao) {
        return leitura(id, (int) id, "5.0", ResultadoTermico.segura(), versao);
    }

    private static Leitura leitura(long id, int minutosDepois, String temperatura, ResultadoTermico resultado,
                                   int versao) {
        return Leitura.reconstituir(id, 1, "S-" + id, ABERTURA.plusMinutes(minutosDepois), new BigDecimal(temperatura),
                "sensor", versao, resultado, ABERTURA.plusMinutes(minutosDepois));
    }

    private static Ocorrencia abrir(List<Long> evidencias, List<LoteAfetado> lotes, boolean existeAberta) {
        var ocorrencia = Ocorrencia.abrir(1, 1, 3, evidencias, lotes, existeAberta, ABERTURA);
        ocorrencia.atribuirId(9);
        return ocorrencia;
    }
}
