package br.ufrn.friovax.api.lote.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.persistencia.CamaraRepositoryPostgres;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LoteRepositoryPostgresTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 26);
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 26, 9, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Inject
    LoteRepositoryPostgres repositorio;

    @Inject
    CamaraRepositoryPostgres camaras;

    private long camara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara " + codigo, "Unidade A", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA)).getId();
    }

    private Lote salvar(String codigo, String imunobiologico, LocalDate validade, int quantidade, long camaraId) {
        return repositorio.salvar(Lote.novo(codigo, imunobiologico, "Fabricante", validade, quantidade, camaraId,
                HOJE, AGORA));
    }

    private void inativar(Lote lote) {
        lote.inativar(AGORA);
        repositorio.salvar(lote);
    }

    /** Descarta o contexto de persistência para que a próxima busca leia do banco, e não do cache da sessão. */
    private void limparContexto() {
        repositorio.getEntityManager().clear();
    }

    @Test
    @TestTransaction
    void deveSalvarEBuscarPorId() {
        var camaraId = camara("C-LOTE");
        var salvo = salvar("fx2027a", "Febre amarela", LocalDate.of(2027, 3, 31), 1200, camaraId);
        limparContexto();

        var encontrado = repositorio.buscarPorId(salvo.getId()).orElseThrow();
        assertEquals("FX2027A", encontrado.getCodigo());
        assertEquals("Febre amarela", encontrado.getImunobiologico());
        assertEquals("Fabricante", encontrado.getFabricante());
        assertEquals(LocalDate.of(2027, 3, 31), encontrado.getValidade());
        assertEquals(1200, encontrado.getQuantidade());
        assertEquals(camaraId, encontrado.getCamaraId());
        assertEquals(EstadoLote.DISPONIVEL, encontrado.getEstado());
        assertTrue(encontrado.isAtivo());
        assertEquals(AGORA.toInstant(), encontrado.getCriadoEm().toInstant());
        assertTrue(repositorio.buscarPorId(Long.MAX_VALUE).isEmpty());
    }

    @Test
    @TestTransaction
    void deveReservarCodigoInclusiveDeLoteInativo() {
        var camaraId = camara("C-DUP");
        inativar(salvar("FX2027A", "Febre amarela", HOJE.plusMonths(6), 100, camaraId));

        assertTrue(repositorio.existePorCodigo("FX2027A"));
        assertFalse(repositorio.existePorCodigo("FX2027B"));
        assertThrows(CodigoDuplicado.class,
                () -> salvar("fx2027a", "Febre amarela", HOJE.plusMonths(6), 1, camaraId));
    }

    @Test
    @TestTransaction
    void naoDeveTratarCamaraInexistenteComoCodigoDuplicado() {
        var lote = Lote.novo("L-ORFAO", "Febre amarela", "Fabricante", HOJE.plusMonths(6), 10, Long.MAX_VALUE,
                HOJE, AGORA);

        assertThrows(PersistenceException.class, () -> repositorio.salvar(lote));
    }

    @Test
    @TestTransaction
    void naoDeveAceitarLoteDisponivelSemDoses() {
        var salvo = salvar("L-VAZIO", "Febre amarela", HOJE.plusMonths(6), 10, camara("C-VAZIO"));
        var semDoses = Lote.reconstituir(salvo.getId(), salvo.getCodigo(), salvo.getImunobiologico(),
                salvo.getFabricante(), salvo.getValidade(), 0, salvo.getCamaraId(), EstadoLote.DISPONIVEL, true,
                AGORA, AGORA);

        assertThrows(PersistenceException.class, () -> repositorio.salvar(semDoses));
    }

    @Test
    @TestTransaction
    void naoDeveAceitarLoteEsgotadoComDoses() {
        var salvo = salvar("L-CHEIO", "Febre amarela", HOJE.plusMonths(6), 10, camara("C-CHEIO"));
        var esgotadoComDoses = Lote.reconstituir(salvo.getId(), salvo.getCodigo(), salvo.getImunobiologico(),
                salvo.getFabricante(), salvo.getValidade(), 10, salvo.getCamaraId(), EstadoLote.ESGOTADO, true,
                AGORA, AGORA);

        assertThrows(PersistenceException.class, () -> repositorio.salvar(esgotadoComDoses));
    }

    @Test
    @TestTransaction
    void deveBuscarComBloqueioParaAlteracao() {
        var salvo = salvar("L-LOCK", "Febre amarela", HOJE.plusMonths(6), 10, camara("C-LOCK"));
        limparContexto();

        assertEquals("L-LOCK", repositorio.buscarPorIdParaAlteracao(salvo.getId()).orElseThrow().getCodigo());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                repositorio.getEntityManager().getLockMode(repositorio.findById(salvo.getId())));
        assertTrue(repositorio.buscarPorIdParaAlteracao(Long.MAX_VALUE).isEmpty());
    }

    @Test
    @TestTransaction
    void deveRejeitarAlteracaoDeIdInexistente() {
        var inexistente = Lote.reconstituir(Long.MAX_VALUE, "L-AUSENTE", "Febre amarela", "Fabricante",
                HOJE.plusMonths(6), 10, camara("C-AUSENTE"), EstadoLote.DISPONIVEL, true, AGORA, AGORA);

        assertThrows(IllegalStateException.class, () -> repositorio.salvar(inexistente));
    }

    @Test
    @TestTransaction
    void deveAtualizarDarBaixaEInativarLotePersistido() {
        var origem = camara("C-ORIGEM");
        var destino = camara("C-DESTINO");
        var lote = salvar("L1", "Febre amarela", HOJE.plusMonths(6), 100, origem);

        lote.atualizar("Hepatite B", "Outro fabricante", HOJE.plusMonths(9), destino, HOJE, AGORA.plusHours(1));
        repositorio.salvar(lote);
        limparContexto();
        var atualizado = repositorio.buscarPorId(lote.getId()).orElseThrow();
        assertEquals("Hepatite B", atualizado.getImunobiologico());
        assertEquals("Outro fabricante", atualizado.getFabricante());
        assertEquals(HOJE.plusMonths(9), atualizado.getValidade());
        assertEquals(destino, atualizado.getCamaraId());

        atualizado.darBaixa(100, MotivoBaixa.ADMINISTRADA, AGORA.plusHours(2));
        repositorio.salvar(atualizado);
        limparContexto();
        var esgotado = repositorio.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(0, esgotado.getQuantidade());
        assertEquals(EstadoLote.ESGOTADO, esgotado.getEstado());

        inativar(esgotado);
        limparContexto();
        assertFalse(repositorio.buscarPorId(lote.getId()).orElseThrow().isAtivo());
    }

    @Test
    @TestTransaction
    void deveFiltrarPorImunobiologicoParcialValidadeInclusivaCamaraEEstado() {
        var primeira = camara("C-FILTRO-1");
        var segunda = camara("C-FILTRO-2");
        salvar("L1", "Febre amarela", LocalDate.of(2027, 1, 1), 10, primeira);
        salvar("L2", "Vacina FEBRE tifoide", LocalDate.of(2027, 6, 30), 10, primeira);
        salvar("L3", "Febre amarela", LocalDate.of(2027, 12, 31), 10, segunda);
        var descartado = salvar("L4", "Hepatite B", LocalDate.of(2027, 6, 30), 10, primeira);
        descartado.descartar(AGORA);
        repositorio.salvar(descartado);

        assertEquals(List.of("L1", "L2", "L3"), codigos(new LoteFiltro("febre", null, null, null, null, true)));
        assertEquals(List.of("L1", "L2"), codigos(new LoteFiltro("febre", null, LocalDate.of(2027, 6, 30),
                null, null, true)));
        assertEquals(List.of("L2", "L4"), codigos(new LoteFiltro(null, LocalDate.of(2027, 6, 30),
                LocalDate.of(2027, 6, 30), null, null, true)));
        assertEquals(List.of("L3"), codigos(new LoteFiltro(null, null, null, segunda, null, true)));
        assertEquals(List.of("L4"), codigos(new LoteFiltro(null, null, null, null, EstadoLote.DESCARTADO, true)));
        assertEquals(List.of(), codigos(new LoteFiltro(null, null, null, Long.MAX_VALUE, null, true)));
    }

    @Test
    @TestTransaction
    void deveTratarCuringasDoTermoComoTextoLiteral() {
        var camaraId = camara("C-CURINGA");
        salvar("L1", "Influenza 100%", HOJE.plusMonths(6), 10, camaraId);
        salvar("L2", "Hepatite_B", HOJE.plusMonths(6), 10, camaraId);
        salvar("L3", "Hepatite B", HOJE.plusMonths(6), 10, camaraId);

        assertEquals(List.of("L1"), codigos(new LoteFiltro("%", null, null, null, null, true)));
        assertEquals(List.of("L2"), codigos(new LoteFiltro("_", null, null, null, null, true)));
    }

    @Test
    @TestTransaction
    void deveListarInativosSomenteQuandoPedidoEPaginarPorId() {
        var camaraId = camara("C-PAGINA");
        salvar("L1", "Febre amarela", HOJE.plusMonths(6), 10, camaraId);
        salvar("L2", "Febre amarela", HOJE.plusMonths(6), 10, camaraId);
        salvar("L3", "Febre amarela", HOJE.plusMonths(6), 10, camaraId);
        inativar(salvar("L4", "Febre amarela", HOJE.plusMonths(6), 10, camaraId));
        var filtro = new LoteFiltro(null, null, null, camaraId, null, true);

        var primeira = repositorio.listar(filtro, new Paginacao(0, 2));
        assertEquals(List.of("L1", "L2"), primeira.itens().stream().map(Lote::getCodigo).toList());
        assertEquals(3, primeira.totalElementos());
        assertEquals(List.of("L3"), repositorio.listar(filtro, new Paginacao(1, 2)).itens().stream()
                .map(Lote::getCodigo).toList());
        assertEquals(List.of("L4"), codigos(new LoteFiltro(null, null, null, camaraId, null, false)));
    }

    @Test
    @TestTransaction
    void deveDevolverPaginaVaziaQuandoODeslocamentoNaoCabeEmInt() {
        var camaraId = camara("C-PAGINA-LONGE");
        for (int i = 1; i <= 6; i++) {
            salvar("L" + i, "Febre amarela", HOJE.plusMonths(6), 10, camaraId);
        }
        var filtro = new LoteFiltro(null, null, null, camaraId, null, true);

        // 42_949_673 * 100 estoura int e daria deslocamento 4; 21_474_837 * 100 daria deslocamento negativo.
        var estouraParaPositivo = repositorio.listar(filtro, new Paginacao(42_949_673, 100));
        assertEquals(List.of(), estouraParaPositivo.itens());
        assertEquals(6, estouraParaPositivo.totalElementos());
        assertEquals(List.of(), repositorio.listar(filtro, new Paginacao(21_474_837, 100)).itens());
    }

    @Test
    @TestTransaction
    void deveSomarApenasLotesAtivosNaOcupacao() {
        var primeira = camara("C-OCUPA-1");
        var segunda = camara("C-OCUPA-2");
        var vazia = camara("C-OCUPA-3");
        salvar("L1", "Febre amarela", HOJE.plusMonths(6), 1200, primeira);
        var segundo = salvar("L2", "Hepatite B", HOJE.plusMonths(6), 300, primeira);
        salvar("L3", "Hepatite B", HOJE.plusMonths(6), 50, segunda);
        inativar(salvar("L4", "Hepatite B", HOJE.plusMonths(6), 999, primeira));

        assertEquals(1500, repositorio.ocupacaoDaCamara(primeira));
        assertEquals(1200, repositorio.ocupacaoDaCamaraExcluindoLote(primeira, segundo.getId()));
        assertEquals(0, repositorio.ocupacaoDaCamara(vazia));
        assertEquals(Map.of(primeira, 1500L, segunda, 50L, vazia, 0L),
                repositorio.ocupacaoPorCamara(List.of(primeira, segunda, vazia)));
        assertEquals(Map.of(), repositorio.ocupacaoPorCamara(List.of()));
    }

    @Test
    @TestTransaction
    void deveLiberarOcupacaoAoInativarOuTrocarDeCamara() {
        var origem = camara("C-TROCA-1");
        var destino = camara("C-TROCA-2");
        var lote = salvar("L1", "Febre amarela", HOJE.plusMonths(6), 1200, origem);

        lote.atualizar("Febre amarela", "Fabricante", lote.getValidade(), destino, HOJE, AGORA);
        repositorio.salvar(lote);
        assertEquals(0, repositorio.ocupacaoDaCamara(origem));
        assertEquals(1200, repositorio.ocupacaoDaCamara(destino));
        assertFalse(repositorio.existeLoteAtivoNaCamara(origem));
        assertTrue(repositorio.existeLoteAtivoNaCamara(destino));

        inativar(lote);
        assertEquals(0, repositorio.ocupacaoDaCamara(destino));
        assertFalse(repositorio.existeLoteAtivoNaCamara(destino));
    }

    private List<String> codigos(LoteFiltro filtro) {
        return repositorio.listar(filtro, Paginacao.padrao()).itens().stream().map(Lote::getCodigo).toList();
    }
}
