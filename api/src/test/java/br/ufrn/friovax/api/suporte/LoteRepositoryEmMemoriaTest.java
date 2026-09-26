package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoteRepositoryEmMemoriaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 26);
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 26, 9, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final LoteRepositoryEmMemoria repositorio = new LoteRepositoryEmMemoria();

    private Lote salvar(String codigo, String imunobiologico, LocalDate validade, int quantidade, long camaraId) {
        return repositorio.salvar(Lote.novo(codigo, imunobiologico, "Fabricante", validade, quantidade, camaraId,
                HOJE, AGORA));
    }

    private void inativar(Lote lote) {
        lote.inativar(AGORA);
        repositorio.salvar(lote);
    }

    @Test
    void deveReservarCodigoInclusiveDeLoteInativo() {
        inativar(salvar("FX2027A", "Febre amarela", HOJE.plusMonths(6), 100, 1));

        assertTrue(repositorio.existePorCodigo("FX2027A"));
        assertThrows(CodigoDuplicado.class, () -> salvar("fx2027a", "Febre amarela", HOJE.plusMonths(6), 1, 1));
    }

    @Test
    void deveSomarApenasLotesAtivosNaOcupacao() {
        salvar("L1", "Febre amarela", HOJE.plusMonths(6), 1200, 1);
        var segundo = salvar("L2", "Hepatite B", HOJE.plusMonths(6), 300, 1);
        salvar("L3", "Hepatite B", HOJE.plusMonths(6), 50, 2);
        inativar(salvar("L4", "Hepatite B", HOJE.plusMonths(6), 999, 1));

        assertEquals(1500, repositorio.ocupacaoDaCamara(1));
        assertEquals(1200, repositorio.ocupacaoDaCamaraExcluindoLote(1, segundo.getId()));
        assertEquals(0, repositorio.ocupacaoDaCamara(3));
        assertEquals(Map.of(1L, 1500L, 2L, 50L, 3L, 0L), repositorio.ocupacaoPorCamara(List.of(1L, 2L, 3L)));
    }

    @Test
    void deveLiberarOcupacaoAoInativarOuTrocarDeCamara() {
        var lote = salvar("L1", "Febre amarela", HOJE.plusMonths(6), 1200, 1);

        lote.atualizar("Febre amarela", "Fabricante", lote.getValidade(), 2, HOJE, AGORA);
        repositorio.salvar(lote);
        assertEquals(0, repositorio.ocupacaoDaCamara(1));
        assertEquals(1200, repositorio.ocupacaoDaCamara(2));
        assertTrue(repositorio.existeLoteAtivoNaCamara(2));

        inativar(lote);
        assertEquals(0, repositorio.ocupacaoDaCamara(2));
        assertFalse(repositorio.existeLoteAtivoNaCamara(2));
    }

    @Test
    void deveFiltrarPorImunobiologicoParcialValidadeInclusivaCamaraEEstado() {
        salvar("L1", "Febre amarela", LocalDate.of(2027, 1, 1), 10, 1);
        salvar("L2", "Vacina FEBRE tifoide", LocalDate.of(2027, 6, 30), 10, 1);
        salvar("L3", "Febre amarela", LocalDate.of(2027, 12, 31), 10, 2);
        var descartado = salvar("L4", "Hepatite B", LocalDate.of(2027, 6, 30), 10, 1);
        descartado.descartar(AGORA);
        repositorio.salvar(descartado);

        assertEquals(List.of("L1", "L2", "L3"), codigos(new LoteFiltro("febre", null, null, null, null, true)));
        assertEquals(List.of("L1", "L2"), codigos(new LoteFiltro("febre", null, LocalDate.of(2027, 6, 30),
                null, null, true)));
        assertEquals(List.of("L2", "L4"), codigos(new LoteFiltro(null, LocalDate.of(2027, 6, 30),
                LocalDate.of(2027, 6, 30), null, null, true)));
        assertEquals(List.of("L3"), codigos(new LoteFiltro(null, null, null, 2L, null, true)));
        assertEquals(List.of("L4"), codigos(new LoteFiltro(null, null, null, null, EstadoLote.DESCARTADO, true)));
        assertEquals(List.of(), codigos(new LoteFiltro(null, null, null, 99L, null, true)));
    }

    @Test
    void deveListarInativosSomenteQuandoPedido() {
        salvar("L1", "Febre amarela", HOJE.plusMonths(6), 10, 1);
        inativar(salvar("L2", "Febre amarela", HOJE.plusMonths(6), 10, 1));

        assertEquals(List.of("L1"), codigos(LoteFiltro.ativos()));
        assertEquals(List.of("L2"), codigos(new LoteFiltro(null, null, null, null, null, false)));
        assertFalse(repositorio.buscarPorId(2).orElseThrow().isAtivo());
    }

    private List<String> codigos(LoteFiltro filtro) {
        return repositorio.listar(filtro, Paginacao.padrao()).itens().stream().map(Lote::getCodigo).toList();
    }
}
