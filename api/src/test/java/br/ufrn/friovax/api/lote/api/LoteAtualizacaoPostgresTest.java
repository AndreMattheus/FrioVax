package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LoteAtualizacaoPostgresTest {
    @Inject CamaraRepository camaras;
    @Inject EntityManager entityManager;

    private final List<Long> camarasDoTeste = new ArrayList<>();

    @AfterEach
    void limparRegistrosDoTeste() {
        QuarkusTransaction.requiringNew().run(() -> camarasDoTeste.forEach(id -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        }));
    }

    @Test
    void rejeitaExcessoESalvaTodosOsValoresAnteriores() {
        long origem = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long destino = novaCamara(500, EstadoCamara.OPERACIONAL);
        long id = cadastrar(origem, 400);
        cadastrar(destino, 200);
        var antes = consultar(id).asString();
        var body = bodyValido(destino, 400);
        body.put("fabricante", "Novo fabricante");

        problema(atualizar(id, body), 409, "capacidade-excedida", id);

        assertEquals(antes, consultar(id).asString());
        assertEquals(400, ocupacao(origem));
        assertEquals(200, ocupacao(destino));
    }

    @Test
    void duasMovimentacoesConcorrentesNaoExcedemDestino() throws Exception {
        long origem1 = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long origem2 = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long destino = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long lote1 = cadastrar(origem1, 600);
        long lote2 = cadastrar(origem2, 600);
        var prontas = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);

        Callable<Response> primeira = () -> moverAoMesmoTempo(lote1, destino, prontas, inicio);
        Callable<Response> segunda = () -> moverAoMesmoTempo(lote2, destino, prontas, inicio);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(primeira);
            var b = executor.submit(segunda);
            assertTrue(prontas.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            var respostaA = a.get(30, TimeUnit.SECONDS);
            var respostaB = b.get(30, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), List.of(respostaA.statusCode(), respostaB.statusCode())
                    .stream().sorted().toList());
            Response rejeitada = respostaA.statusCode() == 409 ? respostaA : respostaB;
            rejeitada.then().body("type", equalTo("/problemas/capacidade-excedida"));
        }

        assertEquals(600, ocupacao(destino));
        assertEquals(600, ocupacao(origem1) + ocupacao(origem2));
    }

    private static Response moverAoMesmoTempo(long id, long destino, CountDownLatch prontas,
                                                CountDownLatch inicio) throws InterruptedException {
        prontas.countDown();
        assertTrue(inicio.await(10, TimeUnit.SECONDS));
        return atualizar(id, bodyValido(destino, 600));
    }

    private long novaCamara(int capacidade, EstadoCamara estado) {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Câmara",
                "UBS teste", capacidade, new BigDecimal("2.0"), new BigDecimal("8.0"), estado,
                OffsetDateTime.now())).getId());
        camarasDoTeste.add(id);
        return id;
    }

    private static long cadastrar(long camaraId, int quantidade) {
        var body = bodyValido(camaraId, quantidade);
        body.put("codigo", "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT));
        return given().contentType("application/json").body(body).post("/api/lotes")
                .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> bodyValido(long camaraId, int quantidade) {
        return new HashMap<>(Map.of(
                "imunobiologico", "Febre amarela",
                "fabricante", "Bio-Manguinhos",
                "validade", LocalDate.now(ZoneId.of("America/Fortaleza")).plusYears(1).toString(),
                "quantidade", quantidade,
                "camaraId", camaraId));
    }

    private static Response atualizar(long id, Object body) {
        return given().contentType("application/json").body(body).put("/api/lotes/{id}", id);
    }

    private static Response consultar(long id) {
        return given().get("/api/lotes/{id}", id).then().statusCode(200).extract().response();
    }

    private static int ocupacao(long camaraId) {
        return given().get("/api/camaras/{id}", camaraId).then().statusCode(200)
                .extract().jsonPath().getInt("ocupacao");
    }

    private static Response problema(Response resposta, int status, String tipo, long id) {
        resposta.then().statusCode(status).contentType("application/problem+json")
                .body("type", equalTo("/problemas/" + tipo))
                .body("instance", equalTo("/api/lotes/" + id));
        return resposta;
    }
}
