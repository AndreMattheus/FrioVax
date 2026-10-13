package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class LoteBaixaDescartePostgresTest {
    private static final String BAIXAS = "/api/lotes/{id}/baixas";

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    private long camaraId;

    @BeforeEach
    void criarCamara() {
        camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Câmara", "UBS teste", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now())).getId());
    }

    @AfterEach
    void apagarRegistros() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", camaraId).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", camaraId).executeUpdate();
        });
    }

    @Test
    void baixaAguardaOutraBaixaEmAndamentoNoMesmoLote() throws Exception {
        long loteId = criarLote(100);
        var loteBloqueado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        try {
            // 1. Uma baixa de 60 doses em andamento: segura o lote e só aplica a baixa quando o teste mandar.
            var primeira = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
                var lote = lotes.buscarPorIdParaAlteracao(loteId).orElseThrow();
                loteBloqueado.countDown();
                aguardar(liberar);
                lote.darBaixa(60, MotivoBaixa.ADMINISTRADA, OffsetDateTime.now());
                lotes.salvar(lote);
            }));
            assertTrue(loteBloqueado.await(10, TimeUnit.SECONDS), "a primeira baixa não bloqueou o lote");

            // 2. Segunda baixa de 60 pela API. Sem bloqueio, ela leria 100 doses e também seria aceita.
            var segunda = CompletableFuture.supplyAsync(() -> baixa(loteId, 60, "ADMINISTRADA").then()
                    .extract().statusCode());

            // 3. O Postgres confirma que a segunda está parada esperando o bloqueio do lote.
            try {
                aguardarSessaoEsperandoBloqueioNaTabelaLotes();
                assertFalse(segunda.isDone(), "a segunda baixa terminou sem esperar o bloqueio do lote");
            } finally {
                liberar.countDown();
            }

            primeira.get(10, TimeUnit.SECONDS);

            // 4. A segunda só continua depois do commit, enxerga 40 doses e é recusada.
            assertEquals(422, segunda.get(10, TimeUnit.SECONDS));
            given().get("/api/lotes/{id}", loteId).then().body("quantidade", equalTo(40));
        } finally {
            liberar.countDown();
        }
    }

    private io.restassured.response.Response baixa(long loteId, int quantidade, String motivo) {
        return given().contentType("application/json")
                .body("{ \"quantidade\": %d, \"motivo\": \"%s\" }".formatted(quantidade, motivo))
                .when().post(BAIXAS, loteId);
    }

    private long criarLote(int quantidade) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var hoje = LocalDate.now();
            return lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), "Vacina", "Fabricante",
                    hoje.plusYears(1), quantidade, camaraId, hoje, OffsetDateTime.now())).getId();
        });
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("o teste não liberou a primeira baixa a tempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Consulta o pg_stat_activity até encontrar uma sessão esperando bloqueio numa consulta à tabela lotes. A pausa
     * entre consultas é só o intervalo de verificação; falha se a espera não aparecer em 10 segundos.
     */
    private void aguardarSessaoEsperandoBloqueioNaTabelaLotes() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            long esperando = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                    .createNativeQuery("""
                            select count(*) from pg_stat_activity
                            where datname = current_database()
                              and wait_event_type = 'Lock'
                              and query ilike '%lotes%'
                            """)
                    .getSingleResult()).longValue());
            if (esperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("a segunda baixa não ficou esperando o bloqueio do lote");
    }
}
