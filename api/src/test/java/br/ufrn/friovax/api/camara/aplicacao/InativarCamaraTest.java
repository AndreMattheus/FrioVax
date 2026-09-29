package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InativarCamaraTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final InativarCamara casoDeUso = new InativarCamara(camaras, lotes,
            Clock.fixed(AGORA.toInstant(), AGORA.getOffset()));

    @Test
    void deveInativarCamaraSemLotesAtivosPreservandoORegistro() {
        var camara = novaCamara("CAM-01");

        casoDeUso.inativar(camara.getId());

        var gravada = camaras.buscarPorId(camara.getId()).orElseThrow();
        assertFalse(gravada.isAtivo());
        assertEquals(AGORA.toInstant(), gravada.getAtualizadoEm().toInstant());
        assertEquals(CRIACAO, gravada.getCriadoEm());
        assertEquals("CAM-01", gravada.getCodigo());
    }

    @Test
    void deveIgnorarLotesJaInativadosDaCamara() {
        var camara = novaCamara("CAM-02");
        var lote = lotes.salvar(novoLote("L-01", camara));
        lote.inativar(CRIACAO.plusHours(1));
        lotes.salvar(lote);

        casoDeUso.inativar(camara.getId());

        assertFalse(camaras.buscarPorId(camara.getId()).orElseThrow().isAtivo());
    }

    @Test
    void deveRecusarCamaraComLotesAtivos() {
        var camara = novaCamara("CAM-03");
        lotes.salvar(novoLote("L-02", camara));

        assertThrows(EstadoIncompativel.class, () -> casoDeUso.inativar(camara.getId()));

        var gravada = camaras.buscarPorId(camara.getId()).orElseThrow();
        assertTrue(gravada.isAtivo());
        assertEquals(CRIACAO, gravada.getAtualizadoEm());
    }

    @Test
    void deveRepetirInativacaoSemAlterarORegistro() {
        var camara = novaCamara("CAM-04");
        casoDeUso.inativar(camara.getId());
        var primeira = camaras.buscarPorId(camara.getId()).orElseThrow().getAtualizadoEm();

        var depois = new InativarCamara(camaras, lotes,
                Clock.fixed(AGORA.plusHours(1).toInstant(), AGORA.getOffset()));
        depois.inativar(camara.getId());

        var gravada = camaras.buscarPorId(camara.getId()).orElseThrow();
        assertFalse(gravada.isAtivo());
        assertEquals(primeira, gravada.getAtualizadoEm());
    }

    @Test
    void deveTirarCamaraInativaDaListagemPadraoEDaAlocacaoDeLotes() {
        var camara = novaCamara("CAM-05");

        casoDeUso.inativar(camara.getId());

        assertTrue(camaras.listar(CamaraFiltro.ativas(), Paginacao.padrao()).itens().isEmpty());
        assertEquals(1, camaras.listar(new CamaraFiltro(null, null, false), Paginacao.padrao()).itens().size());
        var gravada = camaras.buscarPorId(camara.getId()).orElseThrow();
        assertThrows(EstadoIncompativel.class, () -> gravada.verificarAlocacao(0, 1));
    }

    @Test
    void deveIndicarCamaraInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.inativar(42));
    }

    private Camara novaCamara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS Centro", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, CRIACAO));
    }

    private static Lote novoLote(String codigo, Camara camara) {
        return Lote.novo(codigo, "Vacina A", "Fabricante", LocalDate.of(2027, 1, 1),
                100, camara.getId(), CRIACAO.toLocalDate(), CRIACAO);
    }
}
