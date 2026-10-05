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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class CamaraAtualizacaoHttpTest {
    private static final String ROTA = "/api/camaras/{id}";

    @Inject
    CamaraRepository camaras;

    @Inject
    LoteRepository lotes;

    @Inject
    EntityManager entityManager;

    @Test
    void retorna200ComCamposAtualizadosEPreservaIdECodigo() {
        long id = criarCamara();
        String codigo = given().when().get(ROTA, id).then().extract().path("codigo");
        try {
            given().contentType("application/json")
                .body(corpo("Câmara reformada", 2000, "MANUTENCAO", "\"codigo\": \"OUTRO-CODIGO\","))
                .when().put(ROTA, id).then()
                .statusCode(200)
                .body("id", equalTo((int) id),
                    "codigo", equalTo(codigo),
                    "nome", equalTo("Câmara reformada"),
                    "capacidade", equalTo(2000),
                    "estado", equalTo("MANUTENCAO"));
        } finally {
            apagarCamara(id);
        }
    }

    @Test
    void retorna422ParaCorpoInvalidoSemAlterarNada() {
        long id = criarCamara();
        try {
            given().contentType("application/json")
                .body(corpo("Outro nome", 0, "OPERACIONAL", ""))
                .when().put(ROTA, id).then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/validacao"));

            given().when().get(ROTA, id).then()
                .body("nome", equalTo("Câmara de teste"), "capacidade", equalTo(5000));
        } finally {
            apagarCamara(id);
        }
    }

    @Test
    void retorna409AoReduzirCapacidadeAbaixoDaOcupacao() {
        long id = criarCamara();
        try {
            criarLote(id);

            given().contentType("application/json")
                .body(corpo("Câmara de teste", 50, "OPERACIONAL", ""))
                .when().put(ROTA, id).then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/capacidade-excedida"),
                    "instance", equalTo("/api/camaras/" + id));

            given().when().get(ROTA, id).then().body("capacidade", equalTo(5000));
        } finally {
            apagarCamara(id);
        }
    }

    @Test
    void retorna404ParaIdInexistente() {
        given().contentType("application/json")
            .body(corpo("Câmara", 100, "OPERACIONAL", ""))
            .when().put(ROTA, Long.MAX_VALUE).then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/recurso-nao-encontrado"));
    }

    @Test
    void putAguardaBloqueioDaAlocacaoConcorrenteERecusaReducaoDeCapacidade() throws Exception {
        long id = criarCamara();
        var camaraBloqueada = new CountDownLatch(1);
        var liberarAlocacao = new CountDownLatch(1);
        try {
           var alocacao = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
                camaras.buscarPorIdParaAlteracao(id).orElseThrow();
                camaraBloqueada.countDown();
                aguardar(liberarAlocacao);
                criarLoteNaTransacaoAtual(id);
            }));
            assertTrue(camaraBloqueada.await(10, TimeUnit.SECONDS), "a alocação não bloqueou a câmara");

            var put = CompletableFuture.supplyAsync(() -> given().contentType("application/json")
                .body(corpo("Câmara de teste", 50, "OPERACIONAL", ""))
                .when().put(ROTA, id).then().extract().statusCode());

            try {
                aguardarSessaoEsperandoBloqueioNaTabelaCamaras();
                assertFalse(put.isDone(), "o PUT terminou sem esperar o bloqueio da câmara");
            } finally {
                liberarAlocacao.countDown();
            }

            alocacao.get(10, TimeUnit.SECONDS);

            assertEquals(409, put.get(10, TimeUnit.SECONDS));
            given().when().get(ROTA, id).then().body("capacidade", equalTo(5000), "ocupacao", equalTo(100));
        } finally {
            liberarAlocacao.countDown();
            apagarCamara(id);
        }
    }

    private static String corpo(String nome, int capacidade, String estado, String extra) {
        return """
                {
                  %s
                  "nome": "%s",
                  "unidade": "UBS Centro",
                  "capacidade": %d,
                  "temperaturaMinima": 2.0,
                  "temperaturaMaxima": 8.0,
                  "estado": "%s"
                }
                """.formatted(extra, nome, capacidade, estado);
    }

    private long criarCamara() {
        return QuarkusTransaction.requiringNew().call(() -> {
            var codigo = "U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return camaras.salvar(Camara.nova(codigo, "Câmara de teste", "UBS Centro", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora())).getId();
        });
    }

    private void criarLote(long camaraId) {
        QuarkusTransaction.requiringNew().run(() -> criarLoteNaTransacaoAtual(camaraId));
    }

    private void criarLoteNaTransacaoAtual(long camaraId) {
        lotes.salvar(Lote.novo("L-" + UUID.randomUUID().toString().substring(0, 8), "Vacina A", "Fabricante",
            LocalDate.now().plusYears(1), 100, camaraId, LocalDate.now(), agora()));
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.ofHours(-3));
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("o teste não liberou a alocação a tempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Consulta o pg_stat_activity até encontrar uma sessão parada esperando bloqueio (wait_event_type = 'Lock') numa
     * consulta à tabela camaras. A pausa entre consultas é só o intervalo de verificação: o teste avança assim que o
     * banco confirma a espera, e falha se isso não acontecer em 10 segundos.
     */
    private void aguardarSessaoEsperandoBloqueioNaTabelaCamaras() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            long sessoesEsperando = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("""
                            select count(*) from pg_stat_activity
                            where datname = current_database()
                              and wait_event_type = 'Lock'
                              and query ilike '%camaras%'
                            """)
                .getSingleResult()).longValue());
            if (sessoesEsperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("o PUT não ficou esperando o bloqueio da câmara");
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
