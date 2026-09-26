package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamaraRepositoryEmMemoriaTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 26, 9, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final CamaraRepositoryEmMemoria repositorio = new CamaraRepositoryEmMemoria();

    private Camara salvar(String codigo, String unidade, EstadoCamara estado) {
        return repositorio.salvar(Camara.nova(codigo, "Câmara " + codigo, unidade, 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), estado, AGORA));
    }

    @Test
    void deveAtribuirIdsSequenciaisEBuscarPorId() {
        var primeira = salvar("CAM-01", "UBS Centro", EstadoCamara.OPERACIONAL);
        var segunda = salvar("CAM-02", "UBS Centro", EstadoCamara.OPERACIONAL);

        assertEquals(1L, primeira.getId());
        assertEquals(2L, segunda.getId());
        assertEquals("CAM-02", repositorio.buscarPorId(2).orElseThrow().getCodigo());
        assertTrue(repositorio.buscarPorId(3).isEmpty());
    }

    @Test
    void deveReservarCodigoInclusiveDeCamaraInativa() {
        var camara = salvar("CAM-01", "UBS Centro", EstadoCamara.OPERACIONAL);
        camara.inativar(false, AGORA);
        repositorio.salvar(camara);

        assertTrue(repositorio.existePorCodigo("CAM-01"));
        assertThrows(CodigoDuplicado.class, () -> salvar("cam-01", "UBS Norte", EstadoCamara.OPERACIONAL));
    }

    @Test
    void naoDevePersistirAlteracoesSemSalvar() {
        salvar("CAM-01", "UBS Centro", EstadoCamara.OPERACIONAL);
        var carregada = repositorio.buscarPorId(1).orElseThrow();

        carregada.inativar(false, AGORA);

        assertTrue(repositorio.buscarPorId(1).orElseThrow().isAtivo());
        repositorio.salvar(carregada);
        assertFalse(repositorio.buscarPorId(1).orElseThrow().isAtivo());
    }

    @Test
    void deveFiltrarPorUnidadeEstadoEAtivoComE() {
        salvar("CAM-01", "UBS Centro", EstadoCamara.OPERACIONAL);
        salvar("CAM-02", "ubs centro", EstadoCamara.MANUTENCAO);
        salvar("CAM-03", "UBS Norte", EstadoCamara.OPERACIONAL);
        var inativa = salvar("CAM-04", "UBS Centro", EstadoCamara.OPERACIONAL);
        inativa.inativar(false, AGORA);
        repositorio.salvar(inativa);

        var porUnidade = repositorio.listar(new CamaraFiltro("UBS CENTRO", null, true), Paginacao.padrao());
        var combinado = repositorio.listar(new CamaraFiltro("UBS Centro", EstadoCamara.MANUTENCAO, true),
                Paginacao.padrao());
        var inativas = repositorio.listar(new CamaraFiltro(null, null, false), Paginacao.padrao());

        assertEquals(2, porUnidade.totalElementos());
        assertEquals("CAM-02", combinado.itens().getFirst().getCodigo());
        assertEquals(1, combinado.totalElementos());
        assertEquals("CAM-04", inativas.itens().getFirst().getCodigo());
    }

    @Test
    void devePaginarOrdenandoPorId() {
        for (int i = 1; i <= 5; i++) {
            salvar("CAM-0" + i, "UBS", EstadoCamara.OPERACIONAL);
        }

        var segunda = repositorio.listar(CamaraFiltro.ativas(), new Paginacao(1, 2));
        var alemDaUltima = repositorio.listar(CamaraFiltro.ativas(), new Paginacao(5, 2));

        assertEquals("CAM-03", segunda.itens().get(0).getCodigo());
        assertEquals("CAM-04", segunda.itens().get(1).getCodigo());
        assertEquals(5, segunda.totalElementos());
        assertEquals(3, segunda.totalPaginas());
        assertTrue(alemDaUltima.itens().isEmpty());
        assertEquals(5, alemDaUltima.totalElementos());
    }
}
