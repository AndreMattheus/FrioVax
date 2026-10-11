package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultarLoteTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final ConsultarLote casoDeUso = new ConsultarLote(lotes);

    @Test
    void deveConsultarLoteComCamaraESituacao() {
        var lote = lotes.salvar(novoLote("fx2027a"));

        var consultado = casoDeUso.consultar(lote.getId());

        assertEquals(lote.getId(), consultado.getId());
        assertEquals("FX2027A", consultado.getCodigo());
        assertEquals("Febre amarela", consultado.getImunobiologico());
        assertEquals("Bio-Manguinhos", consultado.getFabricante());
        assertEquals(LocalDate.of(2027, 3, 31), consultado.getValidade());
        assertEquals(1200, consultado.getQuantidade());
        assertEquals(7, consultado.getCamaraId());
        assertEquals(EstadoLote.DISPONIVEL, consultado.getEstado());
        assertTrue(consultado.isAtivo());
        assertEquals(AGORA, consultado.getCriadoEm());
        assertEquals(AGORA, consultado.getAtualizadoEm());
    }

    @Test
    void deveConsultarLoteInativo() {
        var lote = lotes.salvar(novoLote("L-INATIVO"));
        lote.inativar(AGORA.plusHours(1));
        lotes.salvar(lote);

        var consultado = casoDeUso.consultar(lote.getId());

        assertFalse(consultado.isAtivo());
        assertEquals(AGORA.plusHours(1), consultado.getAtualizadoEm());
    }

    @Test
    void deveConsultarLoteEsgotado() {
        var lote = lotes.salvar(novoLote("L-ESGOTADO"));
        lote.darBaixa(1200, MotivoBaixa.ADMINISTRADA, AGORA.plusHours(1));
        lotes.salvar(lote);

        var consultado = casoDeUso.consultar(lote.getId());

        assertEquals(EstadoLote.ESGOTADO, consultado.getEstado());
        assertEquals(0, consultado.getQuantidade());
    }

    @Test
    void deveIndicarLoteInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.consultar(42));
    }

    private static Lote novoLote(String codigo) {
        return Lote.novo(codigo, "Febre amarela", "Bio-Manguinhos", LocalDate.of(2027, 3, 31),
                1200, 7, AGORA.toLocalDate(), AGORA);
    }

    @Test
    void deveCombinarFiltrosAntesDePaginarComMetadados() {
        var primeiro = lotes.salvar(novoLote("L-1"));
        var segundo = lotes.salvar(novoLote("L-2"));
        lotes.salvar(Lote.novo("L-OUTRA", "Febre amarela", "Fabricante", primeiro.getValidade(),
                10, 8, AGORA.toLocalDate(), AGORA));
        var descartado = lotes.salvar(novoLote("L-DESCARTADO"));
        descartado.descartar(AGORA.plusHours(1));
        lotes.salvar(descartado);
        var inativo = lotes.salvar(novoLote("L-INATIVO"));
        inativo.inativar(AGORA.plusHours(1));
        lotes.salvar(inativo);
        var filtro = new LoteFiltro("  FEBRE  ", primeiro.getValidade(), primeiro.getValidade(),
                7L, EstadoLote.DISPONIVEL, true);

        var pagina = casoDeUso.listar(filtro, new Paginacao(1, 1));

        assertEquals(List.of(segundo.getId()), pagina.itens().stream().map(Lote::getId).toList());
        assertEquals(1, pagina.pagina());
        assertEquals(1, pagina.tamanho());
        assertEquals(2, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        var distante = casoDeUso.listar(filtro, new Paginacao(Integer.MAX_VALUE, 100));
        assertTrue(distante.itens().isEmpty());
        assertEquals(2, distante.totalElementos());
    }

    @Test
    void deveListarAtivosPorPadraoEInativosQuandoSolicitado() {
        var ativo = lotes.salvar(novoLote("L-ATIVO"));
        var inativo = lotes.salvar(novoLote("L-INATIVO"));
        inativo.inativar(AGORA.plusHours(1));
        lotes.salvar(inativo);

        assertEquals(List.of(ativo.getId()), casoDeUso.listar(LoteFiltro.ativos(), Paginacao.padrao())
                .itens().stream().map(Lote::getId).toList());
        assertEquals(List.of(inativo.getId()), casoDeUso.listar(
                new LoteFiltro(null, null, null, null, null, false), Paginacao.padrao())
                .itens().stream().map(Lote::getId).toList());
    }

    @Test
    void deveRetornarPaginaVaziaParaCamaraSemLotes() {
        lotes.salvar(novoLote("L-1"));

        var pagina = casoDeUso.listar(new LoteFiltro(null, null, null, Long.MAX_VALUE, null, true),
                Paginacao.padrao());

        assertTrue(pagina.itens().isEmpty());
        assertEquals(0, pagina.totalElementos());
        assertEquals(0, pagina.totalPaginas());
        assertEquals(0, pagina.pagina());
        assertEquals(20, pagina.tamanho());
    }

    @Test
    void deveAplicarCadaFiltroAntesDeContarEOrdenarPorId() {
        var de = LocalDate.of(2027, 1, 1);
        var ate = LocalDate.of(2027, 6, 30);
        var primeiro = salvarFiltravel("L-Z", "Febre amarela", de, 7);
        var segundo = salvarFiltravel("L-A", "Vacina FEBRE tifoide", de.plusDays(1), 7);
        var terceiro = salvarFiltravel("L-M", "Febre amarela", ate, 7);
        salvarFiltravel("L-TEXTO", "Hepatite", de, 7);
        salvarFiltravel("L-ANTES", "Febre amarela", de.minusDays(1), 7);
        salvarFiltravel("L-DEPOIS", "Febre amarela", ate.plusDays(1), 7);
        salvarFiltravel("L-CAMARA", "Febre amarela", de, 8);
        var descartado = salvarFiltravel("L-ESTADO", "Febre amarela", de, 7);
        descartado.descartar(AGORA.plusHours(1));
        lotes.salvar(descartado);
        var inativo = salvarFiltravel("L-INATIVO", "Febre amarela", de, 7);
        inativo.inativar(AGORA.plusHours(1));
        lotes.salvar(inativo);
        var filtro = new LoteFiltro("  FEBRE  ", de, ate, 7L, EstadoLote.DISPONIVEL, true);

        var pagina = casoDeUso.listar(filtro, new Paginacao(1, 1));

        assertEquals(List.of(segundo.getId()), pagina.itens().stream().map(Lote::getId).toList());
        assertEquals(1, pagina.pagina());
        assertEquals(1, pagina.tamanho());
        assertEquals(3, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(List.of(primeiro.getId(), segundo.getId(), terceiro.getId()),
                casoDeUso.listar(filtro, Paginacao.padrao()).itens().stream().map(Lote::getId).toList());
        var distante = casoDeUso.listar(filtro, new Paginacao(3, 1));
        assertTrue(distante.itens().isEmpty());
        assertEquals(3, distante.pagina());
        assertEquals(1, distante.tamanho());
        assertEquals(3, distante.totalElementos());
        assertEquals(3, distante.totalPaginas());
    }

    @Test
    void deveFiltrarValidadeComUmUnicoLimiteInclusivo() {
        var limite = LocalDate.of(2027, 3, 31);
        var antes = salvarFiltravel("L-ANTES", "Vacina", limite.minusDays(1), 7);
        var igual = salvarFiltravel("L-IGUAL", "Vacina", limite, 7);
        var depois = salvarFiltravel("L-DEPOIS", "Vacina", limite.plusDays(1), 7);

        var desde = casoDeUso.listar(new LoteFiltro(null, limite, null, null, null, true), Paginacao.padrao());
        var ate = casoDeUso.listar(new LoteFiltro(null, null, limite, null, null, true), Paginacao.padrao());

        assertEquals(List.of(igual.getId(), depois.getId()), desde.itens().stream().map(Lote::getId).toList());
        assertEquals(List.of(antes.getId(), igual.getId()), ate.itens().stream().map(Lote::getId).toList());
        assertEquals(2, desde.totalElementos());
        assertEquals(1, desde.totalPaginas());
        assertEquals(2, ate.totalElementos());
        assertEquals(1, ate.totalPaginas());
    }

    private Lote salvarFiltravel(String codigo, String imunobiologico, LocalDate validade, long camaraId) {
        return lotes.salvar(Lote.novo(codigo, imunobiologico, "Fabricante", validade,
                10, camaraId, AGORA.toLocalDate(), AGORA));
    }
}
