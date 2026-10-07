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
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LoteAtualizacaoHttpTest {
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
    void atualizaCamposEAumentaQuantidadeSemContarProprioLoteDuasVezes() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 600);
        var antes = consultar(id);
        var body = bodyValido(camaraId, 1000);
        body.put("imunobiologico", " Outra vacina ");
        body.put("codigo", "CODIGO-IGNORADO");

        atualizar(id, body).then().statusCode(200)
                .body("codigo", equalTo(antes.jsonPath().getString("codigo")))
                .body("imunobiologico", equalTo("Outra vacina"))
                .body("quantidade", equalTo(1000))
                .body("camaraId", equalTo((int) camaraId))
                .body("criadoEm", equalTo(antes.jsonPath().getString("criadoEm")));
        assertEquals(1000, ocupacao(camaraId));
    }

    @Test
    void trocaDeCamaraLiberaOrigemEAtualizaDestino() {
        long origem = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long destino = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(origem, 600);
        cadastrar(destino, 300);

        atualizar(id, bodyValido(destino, 700)).then().statusCode(200)
                .body("camaraId", equalTo((int) destino))
                .body("quantidade", equalTo(700));
        assertEquals(0, ocupacao(origem));
        assertEquals(1000, ocupacao(destino));
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
    void rejeitaReducaoEVencimentoSemAlterarLote() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 400);
        var antes = consultar(id).asString();
        var body = bodyValido(camaraId, 399);
        problema(atualizar(id, body), 422, "validacao", id)
                .then().body("erros.campo", hasItem("quantidade"));

        body.put("quantidade", 400);
        body.put("validade", LocalDate.now(ZoneId.of("America/Fortaleza")).toString());
        problema(atualizar(id, body), 422, "validacao", id)
                .then().body("erros.campo", hasItem("validade"));

        assertEquals(antes, consultar(id).asString());
    }

    @Test
    void validaBodyECamaraDeDestino() {
        long origem = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(origem, 100);
        var antes = consultar(id).asString();
        for (String campo : List.of("imunobiologico", "fabricante", "validade", "quantidade", "camaraId")) {
            var body = bodyValido(origem, 100);
            body.remove(campo);
            problema(atualizar(id, body), 422, "validacao", id)
                    .then().body("erros.campo", hasItem(campo));
        }
        problema(given().contentType("application/json").put("/api/lotes/{id}", id),
                422, "validacao", id).then().body("erros.campo", hasItem("body"));
        problema(atualizar(id, bodyValido(Long.MAX_VALUE, 100)), 404, "recurso-nao-encontrado", id);

        long manutencao = novaCamara(1000, EstadoCamara.MANUTENCAO);
        problema(atualizar(id, bodyValido(manutencao, 100)), 409, "estado-incompativel", id);
        long inativa = novaCamara(1000, EstadoCamara.OPERACIONAL);
        given().delete("/api/camaras/{id}", inativa).then().statusCode(204);
        problema(atualizar(id, bodyValido(inativa, 100)), 409, "estado-incompativel", id);

        assertEquals(antes, consultar(id).asString());
    }

    @Test
    void rejeitaAlteracaoDeLoteInativoEIdInexistente() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 100);
        given().delete("/api/lotes/{id}", id).then().statusCode(204);
        problema(atualizar(id, bodyValido(camaraId, 100)), 409, "estado-incompativel", id);
        problema(atualizar(Long.MAX_VALUE, bodyValido(camaraId, 100)),
                404, "recurso-nao-encontrado", Long.MAX_VALUE);
        assertEquals(0, ocupacao(camaraId));
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
