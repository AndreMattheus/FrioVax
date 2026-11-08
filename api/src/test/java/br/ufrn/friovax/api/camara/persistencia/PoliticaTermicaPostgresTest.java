package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Persistência da política térmica versionada (S2-05). Cada operação confirma a própria transação. */
@QuarkusTest
class PoliticaTermicaPostgresTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-11-07T10:00:00-03:00");

    @Inject
    CamaraRepositoryPostgres repositorio;

    @Inject
    EntityManager entityManager;

    private Long camaraId;

    @AfterEach
    void apagarCamara() {
        if (camaraId != null) {
            // A remoção física só existe na limpeza de testes; as versões vão junto (ON DELETE CASCADE).
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", camaraId).executeUpdate());
        }
    }

    @Test
    void cadastroGravaAVersaoUmEOEstadoSemLeituras() {
        camaraId = criar("2.0", "8.0");

        var camara = buscar();
        assertEquals(1, camara.getPolitica().getVersao());
        assertEquals(new BigDecimal("2.0"), camara.getTemperaturaMinima());
        assertEquals(3, camara.getPolitica().getLimiar());
        assertEquals(EstadoTermico.SEM_LEITURAS, camara.getEstadoTermico());
        assertEquals(1, historico().size());
    }

    @Test
    void alteracaoDaFaixaGravaNovaVersaoEPreservaAAnterior() {
        camaraId = criar("2.0", "8.0");

        alterar("3.0", "7.0", 3);

        var camara = buscar();
        assertEquals(2, camara.getPolitica().getVersao());
        assertEquals(new BigDecimal("3.0"), camara.getTemperaturaMinima());
        var historico = historico();
        assertEquals(2, historico.size());
        assertEquals(new BigDecimal("2.0"), historico.get(0).getTemperaturaMinima(), "a versão 1 não muda");
        assertEquals(new BigDecimal("3.0"), historico.get(1).getTemperaturaMinima());
    }

    @Test
    void salvarSemMudancaNaoGravaNovaVersao() {
        camaraId = criar("2.0", "8.0");

        alterar("2.0", "8.0", 3);
        QuarkusTransaction.requiringNew().run(() -> repositorio.salvar(repositorio.buscarPorId(camaraId).orElseThrow()));

        assertEquals(1, historico().size());
    }

    @Test
    void falhaNaTransacaoDesfazANovaVersao() {
        camaraId = criar("2.0", "8.0");

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            var camara = repositorio.buscarPorIdParaAlteracao(camaraId).orElseThrow();
            camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 5, null, CRIACAO.plusHours(1));
            repositorio.salvar(camara);
            throw new IllegalStateException("falha depois de gravar, dentro da mesma transação");
        }));

        var camara = buscar();
        assertEquals(1, camara.getPolitica().getVersao());
        assertEquals(new BigDecimal("2.0"), camara.getTemperaturaMinima());
        assertEquals(1, historico().size());
    }

    @Test
    void listagemTrazAFaixaDaVersaoVigente() {
        camaraId = criar("2.0", "8.0");
        alterar("4.5", "6.5", 3);
        var unidade = buscar().getUnidade();

        var pagina = QuarkusTransaction.requiringNew().call(() -> repositorio.listar(
                new CamaraFiltro(unidade, null, true), new Paginacao(0, 20)));

        assertEquals(1, pagina.itens().size());
        assertEquals(new BigDecimal("4.5"), pagina.itens().get(0).getTemperaturaMinima());
        assertEquals(new BigDecimal("6.5"), pagina.itens().get(0).getTemperaturaMaxima());
    }

    private long criar(String minima, String maxima) {
        var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return QuarkusTransaction.requiringNew().call(() -> repositorio.salvar(Camara.nova("P-" + sufixo,
                "Câmara", "UBS " + sufixo, 100, new BigDecimal(minima), new BigDecimal(maxima),
                EstadoCamara.OPERACIONAL, CRIACAO)).getId());
    }

    private void alterar(String minima, String maxima, int limiar) {
        QuarkusTransaction.requiringNew().run(() -> {
            var camara = repositorio.buscarPorIdParaAlteracao(camaraId).orElseThrow();
            camara.alterarPolitica(new BigDecimal(minima), new BigDecimal(maxima), limiar, null,
                    CRIACAO.plusHours(1));
            repositorio.salvar(camara);
        });
    }

    private List<PoliticaTermica> historico() {
        return QuarkusTransaction.requiringNew().call(() -> repositorio.historicoDePoliticas(camaraId));
    }

    private Camara buscar() {
        return QuarkusTransaction.requiringNew().call(() -> repositorio.buscarPorId(camaraId).orElseThrow());
    }
}
