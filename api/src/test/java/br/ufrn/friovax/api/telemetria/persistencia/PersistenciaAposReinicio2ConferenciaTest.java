package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Segunda metade da prova de persistência após reinício da API: roda noutra aplicação Quarkus, iniciada depois de
 * encerrar a que gravou ({@link PersistenciaAposReinicio1GravacaoTest}), e confere que tudo continua no banco.
 * Para executar só esta prova: {@code mvn -f api/pom.xml test -Dtest='PersistenciaAposReinicio*'}.
 */
@QuarkusTest
@TestProfile(ReinicioDaApi.DepoisDoReinicio.class)
class PersistenciaAposReinicio2ConferenciaTest {

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject LeituraRepositoryPostgres leituras;
    @Inject OcorrenciaRepositoryPostgres ocorrencias;
    @Inject EntityManager entityManager;
    @Inject IdentidadeDaAplicacao aplicacao;

    @AfterEach
    void apagar() {
        // Apaga todas as gravações do reinício, inclusive sobras de execuções interrompidas.
        // Ordem importa: a fotografia aponta para o lote, e o lote não tem cascata.
        var filtro = " where camara_id in (select id from camaras where codigo like :prefixo)";
        QuarkusTransaction.requiringNew().run(() -> {
            for (var sql : List.of("delete from ocorrencias" + filtro, "delete from lotes" + filtro,
                    "delete from camaras where codigo like :prefixo")) {
                entityManager.createNativeQuery(sql)
                        .setParameter("prefixo", ReinicioDaApi.PREFIXO_CODIGO + "%").executeUpdate();
            }
        });
    }

    @Test
    void dadosGravadosAntesDoReinicioContinuamNoBanco() {
        List<?> gravadas = QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery(
                        "select id, nome from camaras where codigo like :prefixo order by id desc")
                .setParameter("prefixo", ReinicioDaApi.PREFIXO_CODIGO + "%").getResultList());
        if (gravadas.isEmpty()) {
            assumeTrue(ReinicioDaApi.bancoSobreviveAoReinicio(), "Sem URL de banco, o Dev Services recria o "
                    + "PostgreSQL no reinício: não há o mesmo banco para conferir.");
            fail("Nada foi gravado antes do reinício. Execute junto com PersistenciaAposReinicio1GravacaoTest, "
                    + "por exemplo com -Dtest='PersistenciaAposReinicio*'.");
        }
        var maisRecente = (Object[]) gravadas.get(0);
        long camaraId = ((Number) maisRecente[0]).longValue();

        assertNotEquals(ReinicioDaApi.nomeDaCamara(aplicacao.id()), maisRecente[1],
                "a câmara deveria ter sido gravada por outra aplicação, antes do reinício");
        assertTrue(QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(camaraId)).isPresent());

        var ids = new ArrayList<Long>();
        for (int minutos = 1; minutos <= 3; minutos++) {
            var idExterno = "R-" + minutos;
            Leitura lida = QuarkusTransaction.requiringNew()
                    .call(() -> leituras.buscarPorChave(camaraId, idExterno).orElseThrow());
            ids.add(lida.getId());
            assertTrue(lida.getInstante().isEqual(PersistenciaAposReinicio1GravacaoTest.T0.plusMinutes(minutos)),
                    "instante preservado com os microssegundos: " + lida.getInstante());
            assertEquals(0, new BigDecimal("9.5").compareTo(lida.getTemperatura()));
            assertEquals("sensor-1", lida.getOrigem());
            assertEquals(1, lida.getPoliticaVersao());
            assertEquals(ResultadoTermico.insegura(minutos, minutos == 3), lida.getResultado());
        }

        var ocorrencia = QuarkusTransaction.requiringNew()
                .call(() -> ocorrencias.abertaDaCamara(camaraId).orElseThrow());
        assertEquals(ids.get(2), ocorrencia.getLeituraAberturaId());
        assertEquals(ids, ocorrencia.getEvidencias());

        assertEquals(1, ocorrencia.getLotesAfetados().size());
        LoteAfetado fotografado = ocorrencia.getLotesAfetados().get(0);
        assertEquals(ReinicioDaApi.IMUNOBIOLOGICO, fotografado.imunobiologico());
        assertEquals(100, fotografado.quantidade());
        assertEquals(fotografado.codigo(), QuarkusTransaction.requiringNew()
                .call(() -> lotes.buscarPorId(fotografado.loteId()).orElseThrow().getCodigo()));
    }
}
