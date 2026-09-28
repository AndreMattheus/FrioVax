package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CamaraRepositoryPostgresTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 25, 9, 0, 0, 0, ZoneOffset.UTC);

    @Inject
    CamaraRepositoryPostgres repositorio;

    private static Camara novaCamara(String codigo, String nome, String unidade, int capacidade,
                                     String temperaturaMinima, String temperaturaMaxima) {
        return Camara.nova(codigo, nome, unidade, capacidade,
            new BigDecimal(temperaturaMinima), new BigDecimal(temperaturaMaxima),
            EstadoCamara.OPERACIONAL, AGORA);
    }

    @Test
    @TestTransaction
    void deveSalvarEBuscarPorId() {
        var camara = novaCamara("C-100", "Câmara Teste", "Unidade A", 10, "2.0", "8.0");

        var salva = repositorio.salvar(camara);

        var encontrada = repositorio.buscarPorId(salva.getId());
        assertTrue(encontrada.isPresent());
        assertEquals("C-100", encontrada.orElseThrow().getCodigo());
    }

    @Test
    @TestTransaction
    void deveLancarCodigoDuplicadoAoSalvarCodigoExistente() {
        var camara1 = novaCamara("C-DUP", "Primeira", "Unidade A", 10, "2.0", "8.0");
        repositorio.salvar(camara1);

        var camara2 = novaCamara("C-DUP", "Segunda", "Unidade B", 5, "1.0", "5.0");

        assertThrows(CodigoDuplicado.class, () -> repositorio.salvar(camara2));
    }

    @Test
    @TestTransaction
    void naoDeveTratarOutraConstraintComoCodigoDuplicado() {
        var salva = repositorio.salvar(novaCamara("C-INVALIDA", "Inválida", "Unidade A", 10,
                "2.0", "8.0"));
        var invalida = Camara.reconstituir(salva.getId(), salva.getCodigo(), salva.getNome(), salva.getUnidade(), 0,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                true, AGORA, AGORA);

        assertThrows(PersistenceException.class, () -> repositorio.salvar(invalida));
    }

    @Test
    @TestTransaction
    void deveRejeitarAlteracaoDeIdInexistente() {
        var inexistente = Camara.reconstituir(Long.MAX_VALUE, "C-AUSENTE", "Ausente", "Unidade A", 10,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                true, AGORA, AGORA);

        assertThrows(IllegalStateException.class, () -> repositorio.salvar(inexistente));
    }

    @Test
    @TestTransaction
    void deveBuscarComLockEVerificarCodigo() {
        var camara = repositorio.salvar(novaCamara("C-LOCK", "Câmara", "Unidade X", 10, "2.0", "8.0"));

        assertEquals(camara.getCodigo(), repositorio.buscarPorIdParaAlteracao(camara.getId())
                .orElseThrow().getCodigo());
        assertTrue(repositorio.buscarPorIdParaAlteracao(Long.MAX_VALUE).isEmpty());
        assertTrue(repositorio.existePorCodigo("C-LOCK"));
        assertFalse(repositorio.existePorCodigo("C-OUTRO"));
    }

    @Test
    @TestTransaction
    void deveAtualizarEInativarCamaraPersistida() {
        var camara = repositorio.salvar(novaCamara("C-UPDATE", "Original", "Unidade A", 10, "2.0", "8.0"));

        camara.atualizar("Atualizada", "Unidade B", 20, new BigDecimal("1.0"),
            new BigDecimal("7.0"), EstadoCamara.MANUTENCAO, 0, false, AGORA.plusHours(1));
        repositorio.salvar(camara);

        var atualizada = repositorio.buscarPorId(camara.getId()).orElseThrow();
        assertEquals("Atualizada", atualizada.getNome());
        assertEquals("Unidade B", atualizada.getUnidade());
        assertEquals(20, atualizada.getCapacidade());
        assertEquals(EstadoCamara.MANUTENCAO, atualizada.getEstado());

        atualizada.inativar(false, AGORA.plusHours(2));
        repositorio.salvar(atualizada);

        assertFalse(repositorio.buscarPorId(camara.getId()).orElseThrow().isAtivo());
        assertEquals(1, repositorio.listar(new CamaraFiltro("Unidade B", EstadoCamara.MANUTENCAO, false),
            Paginacao.padrao()).totalElementos());
    }

    @Test
    @TestTransaction
    void deveListarComFiltroEPaginacao() {
        for (int i = 1; i <= 3; i++) {
            repositorio.salvar(novaCamara("C-LIST-" + i, "Câmara " + i, "Unidade X", 10,
                "2.0", "8.0"));
        }

        var filtro = new CamaraFiltro("unidade x", null, true);
        var pagina = repositorio.listar(filtro, new Paginacao(0, 2));

        assertEquals(2, pagina.itens().size());
        assertEquals(3, pagina.totalElementos());
        assertTrue(pagina.itens().stream().allMatch(camara -> camara.getUnidade().equals("Unidade X")));
    }

    @Test
    @TestTransaction
    void deveDevolverPaginaVaziaQuandoODeslocamentoNaoCabeEmInt() {
        for (int i = 1; i <= 6; i++) {
            repositorio.salvar(novaCamara("C-LONGE-" + i, "Câmara " + i, "Unidade Longe", 10, "2.0", "8.0"));
        }
        var filtro = new CamaraFiltro("Unidade Longe", null, true);

        // 42_949_673 * 100 estoura int e daria deslocamento 4; 21_474_837 * 100 daria deslocamento negativo.
        var estouraParaPositivo = repositorio.listar(filtro, new Paginacao(42_949_673, 100));
        assertTrue(estouraParaPositivo.itens().isEmpty());
        assertEquals(6, estouraParaPositivo.totalElementos());
        assertTrue(repositorio.listar(filtro, new Paginacao(21_474_837, 100)).itens().isEmpty());
    }
}
