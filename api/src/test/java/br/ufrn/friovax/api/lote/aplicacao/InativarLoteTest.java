package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InativarLoteTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-10-01T10:00:00-03:00");
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final InativarLote casoDeUso = new InativarLote(lotes,
            Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza")));

    @Test
    void devePreservarRegistroELiberarSomenteSuaOcupacao() {
        var lote = lotes.salvar(novo("L1", 30, 1));
        lotes.salvar(novo("L2", 20, 1));
        lotes.salvar(novo("L3", 50, 2));
        assertEquals(50, lotes.ocupacaoDaCamara(1));

        casoDeUso.inativar(lote.getId());

        assertInativado(lote);
        assertEquals(20, lotes.ocupacaoDaCamara(1));
        assertEquals(50, lotes.ocupacaoDaCamara(2));
        assertEquals(2, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
        assertEquals(lote.getId(), lotes.listar(new LoteFiltro(null, null, null, null, null, false),
                Paginacao.padrao()).itens().getFirst().getId());
        assertTrue(lotes.existePorCodigo("L1"));
        assertThrows(CodigoDuplicado.class, () -> lotes.salvar(novo("l1", 1, 1)));
    }

    @Test
    void deveRepetirInativacaoSemMudarDataOuLiberarCapacidadeNovamente() {
        var lote = lotes.salvar(novo("L1", 30, 1));
        lotes.salvar(novo("L2", 20, 1));
        casoDeUso.inativar(lote.getId());

        var depois = new InativarLote(lotes, Clock.fixed(AGORA.plusDays(1).toInstant(),
                ZoneId.of("America/Fortaleza")));
        depois.inativar(lote.getId());

        assertInativado(lote);
        assertEquals(20, lotes.ocupacaoDaCamara(1));
    }

    @Test
    void deveRejeitarIdInexistenteSemAlterarLoteExistente() {
        var lote = lotes.salvar(novo("L1", 30, 1));

        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.inativar(999));

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertTrue(salvo.isAtivo());
        assertEquals(CRIACAO, salvo.getAtualizadoEm());
        assertEquals(30, lotes.ocupacaoDaCamara(1));
        assertEquals(1, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
        assertTrue(lotes.buscarPorId(999).isEmpty());
    }

    private void assertInativado(Lote anterior) {
        var salvo = lotes.buscarPorId(anterior.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(anterior.getId(), salvo.getId()),
                () -> assertEquals(anterior.getCodigo(), salvo.getCodigo()),
                () -> assertEquals(anterior.getImunobiologico(), salvo.getImunobiologico()),
                () -> assertEquals(anterior.getFabricante(), salvo.getFabricante()),
                () -> assertEquals(anterior.getValidade(), salvo.getValidade()),
                () -> assertEquals(anterior.getQuantidade(), salvo.getQuantidade()),
                () -> assertEquals(anterior.getCamaraId(), salvo.getCamaraId()),
                () -> assertEquals(anterior.getEstado(), salvo.getEstado()),
                () -> assertEquals(CRIACAO, salvo.getCriadoEm()),
                () -> assertEquals(AGORA, salvo.getAtualizadoEm()),
                () -> assertFalse(salvo.isAtivo()));
    }

    private static Lote novo(String codigo, int quantidade, long camaraId) {
        return Lote.novo(codigo, "Vacina", "Fabricante", LocalDate.of(2027, 1, 1),
                quantidade, camaraId, CRIACAO.toLocalDate(), CRIACAO);
    }
}
