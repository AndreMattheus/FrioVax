package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CamaraInativacaoPostgresTest {
    private static final String ROTA = "/api/camaras/{id}";

    @Inject
    CamaraRepository camaras;

    @Inject
    LoteRepository lotes;

    @Inject
    EntityManager entityManager;

    @Test
    void aguardaAlocacaoConcorrenteERecusaAInativacao() throws Exception {
        long id = criarCamara();
        var camaraBloqueada = new CountDownLatch(1);
        try {
            // Simula uma alocação em andamento: bloqueia a câmara, como o cadastro de lotes faz (§3.2), e só grava o
            // lote depois que o DELETE já foi disparado.
            var alocacao = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
                camaras.buscarPorIdParaAlteracao(id).orElseThrow();
                camaraBloqueada.countDown();
                esperar(500);
                criarLoteNaTransacaoAtual(id);
            }));
            assertTrue(camaraBloqueada.await(10, TimeUnit.SECONDS));

            int status = given().when().delete(ROTA, id).then().extract().statusCode();

            alocacao.get(10, TimeUnit.SECONDS);
            assertEquals(409, status);
            given().when().get(ROTA, id).then().body("ativo", equalTo(true), "ocupacao", equalTo(100));
        } finally {
            apagarCamara(id);
        }
    }

    private long criarCamara() {
        return QuarkusTransaction.requiringNew().call(() -> {
            var codigo = "D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return camaras.salvar(Camara.nova(codigo, "Câmara de teste", "UBS Centro", 5000,
                    new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora())).getId();
        });
    }

    private void criarLoteNaTransacaoAtual(long camaraId) {
        lotes.salvar(Lote.novo("L-" + UUID.randomUUID().toString().substring(0, 8), "Vacina A", "Fabricante",
                LocalDate.now().plusYears(1), 100, camaraId, LocalDate.now(), agora()));
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.ofHours(-3));
    }

    private static void esperar(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void apagarCamara(long id) {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        });
    }
}
