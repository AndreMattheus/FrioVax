package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras da política térmica dentro da câmara (S2-05; ADR 0003, seções 2 e 4). */
class CamaraPoliticaTermicaTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-11-07T10:00:00-03:00");
    private static final OffsetDateTime DEPOIS = CRIACAO.plusHours(2);

    @Test
    void camaraNovaComecaNaVersaoUmSemLeituras() {
        var camara = nova();

        assertEquals(1, camara.getPolitica().getVersao());
        assertEquals(PoliticaTermica.LIMIAR_INICIAL, camara.getPolitica().getLimiar());
        assertEquals(new BigDecimal("2.0"), camara.getTemperaturaMinima());
        assertEquals(EstadoTermico.SEM_LEITURAS, camara.getEstadoTermico());
        assertEquals(EstadoCamara.OPERACIONAL, camara.getEstado(), "o estado operacional não é alterado");
    }

    @Test
    void putSemMudarAFaixaNaoGeraVersao() {
        var camara = nova();
        var politicaAntes = camara.getPolitica();

        camara.atualizar("Outro nome", "UBS Norte", 900, new BigDecimal("2"), new BigDecimal("8.00"),
                EstadoCamara.OPERACIONAL, 0, false, DEPOIS);

        assertSame(politicaAntes, camara.getPolitica());
        assertEquals("Outro nome", camara.getNome(), "os demais campos continuam sendo atualizados");
    }

    @Test
    void putComNovaFaixaGeraVersaoPreservandoOLimiar() {
        var camara = comLimiar(5);
        var autor = UUID.randomUUID();

        camara.atualizar("Câmara", "UBS Centro", 1000, new BigDecimal("3.0"), new BigDecimal("7.0"),
                EstadoCamara.OPERACIONAL, 0, false, autor, DEPOIS.plusHours(1));

        var politica = camara.getPolitica();
        assertEquals(3, politica.getVersao());
        assertEquals(new BigDecimal("3.0"), politica.getTemperaturaMinima());
        assertEquals(5, politica.getLimiar(), "mudança só de faixa preserva o limiar");
        assertEquals(autor, politica.getAutorId().orElseThrow());
    }

    @Test
    void novaVersaoVoltaParaSemLeiturasQuandoNaoHaOcorrenciaAberta() {
        var camara = comEstadoTermico(EstadoTermico.ATENCAO);

        camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 3, null, DEPOIS);

        assertEquals(EstadoTermico.SEM_LEITURAS, camara.getEstadoTermico());
    }

    @Test
    void novaVersaoMantemEmAlertaComOcorrenciaAberta() {
        var camara = comEstadoTermico(EstadoTermico.EM_ALERTA);

        camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 3, null, DEPOIS);

        assertEquals(EstadoTermico.EM_ALERTA, camara.getEstadoTermico());
    }

    @Test
    void alterarPoliticaComOsMesmosValoresNaoGeraVersao() {
        var camara = nova();

        assertFalse(camara.alterarPolitica(new BigDecimal("2.0"), new BigDecimal("8.0"), 3, null, DEPOIS));
        assertEquals(1, camara.getPolitica().getVersao());
        assertEquals(CRIACAO, camara.getAtualizadoEm());
    }

    @Test
    void alterarSoOLimiarGeraVersao() {
        var camara = nova();

        assertTrue(camara.alterarPolitica(new BigDecimal("2.0"), new BigDecimal("8.0"), 4, null, DEPOIS));
        assertEquals(2, camara.getPolitica().getVersao());
        assertEquals(4, camara.getPolitica().getLimiar());
        assertEquals(DEPOIS, camara.getAtualizadoEm());
    }

    @Test
    void faixaInvalidaNoPutNaoAlteraNada() {
        var camara = nova();
        var politicaAntes = camara.getPolitica();

        assertThrows(ValidacaoDeNegocio.class, () -> camara.atualizar("Outro nome", "UBS Norte", 900,
                new BigDecimal("8.0"), new BigDecimal("2.0"), EstadoCamara.OPERACIONAL, 0, false, DEPOIS));

        assertSame(politicaAntes, camara.getPolitica());
        assertEquals("Câmara", camara.getNome());
        assertEquals(CRIACAO, camara.getAtualizadoEm());
    }

    @Test
    void capacidadeRecusadaNaoGeraVersao() {
        var camara = nova();

        assertThrows(CapacidadeExcedida.class, () -> camara.atualizar("Câmara", "UBS Centro", 10,
                new BigDecimal("3.0"), new BigDecimal("7.0"), EstadoCamara.OPERACIONAL, 50, false, DEPOIS));

        assertEquals(1, camara.getPolitica().getVersao());
        assertEquals(EstadoTermico.SEM_LEITURAS, camara.getEstadoTermico());
    }

    @Test
    void limiarInvalidoNaoAlteraNada() {
        var camara = nova();

        assertThrows(ValidacaoDeNegocio.class,
                () -> camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 0, null, DEPOIS));
        assertThrows(ValidacaoDeNegocio.class,
                () -> camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 101, null, DEPOIS));

        assertEquals(1, camara.getPolitica().getVersao());
    }

    private static Camara nova() {
        return Camara.nova("CAM-01", "Câmara", "UBS Centro", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL, CRIACAO);
    }

    private static Camara comLimiar(int limiar) {
        var politica = PoliticaTermica.reconstituir(2, new BigDecimal("2.0"), new BigDecimal("8.0"), limiar, null,
                CRIACAO);
        return Camara.reconstituir(1, "CAM-01", "Câmara", "UBS Centro", 1000, politica, EstadoTermico.NORMAL,
                EstadoCamara.OPERACIONAL, true, CRIACAO, CRIACAO);
    }

    private static Camara comEstadoTermico(EstadoTermico estado) {
        var politica = PoliticaTermica.reconstituir(1, new BigDecimal("2.0"), new BigDecimal("8.0"), 3, null,
                CRIACAO);
        return Camara.reconstituir(1, "CAM-01", "Câmara", "UBS Centro", 1000, politica, estado,
                EstadoCamara.OPERACIONAL, true, CRIACAO, CRIACAO);
    }
}
