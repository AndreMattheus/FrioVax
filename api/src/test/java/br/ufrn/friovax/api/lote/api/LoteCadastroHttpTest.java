package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
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
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LoteCadastroHttpTest {
    private static final String ROTA = "/api/lotes";

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    private final List<Long> camarasDoTeste = new ArrayList<>();

    @AfterEach
    void limparRegistrosDoTeste() {
        // As requisições HTTP confirmam suas próprias transações; a limpeza usa apenas as câmaras deste teste.
        QuarkusTransaction.requiringNew().run(() -> camarasDoTeste.forEach(id -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        }));
    }

    @Test
    void cadastraLoteComLocationECorpo() {
        long camaraId = novaCamara(5000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 1200);
        var codigo = (String) body.get("codigo");
        body.put("codigo", "  " + codigo.toLowerCase(Locale.ROOT) + "  ");

        var resposta = cadastrar(body);

        resposta.then().statusCode(201)
                .body("codigo", equalTo(codigo))
                .body("imunobiologico", equalTo("Febre amarela"))
                .body("fabricante", equalTo("Bio-Manguinhos"))
                .body("validade", equalTo(body.get("validade")))
                .body("quantidade", equalTo(1200))
                .body("camaraId", equalTo((int) camaraId))
                .body("estado", equalTo("DISPONIVEL"))
                .body("ativo", equalTo(true))
                .body("criadoEm", not(blankOrNullString()));
        long id = resposta.jsonPath().getLong("id");
        assertEquals("/api/lotes/" + id, URI.create(resposta.header("Location")).getPath());
        given().get("/api/lotes/{id}", id).then().statusCode(200).body("codigo", equalTo(codigo));
        given().get("/api/camaras/{id}", camaraId).then().statusCode(200).body("ocupacao", equalTo(1200));
    }

    @Test
    void aceitaOcupacaoIgualACapacidadeERecusaExcedenteSemGravar() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        cadastrar(bodyValido(camaraId, 600)).then().statusCode(201);
        cadastrar(bodyValido(camaraId, 400)).then().statusCode(201);

        var excedente = bodyValido(camaraId, 1);
        verificarProblema(cadastrar(excedente), 409, "capacidade-excedida");

        QuarkusTransaction.requiringNew().run(() -> {
            assertFalse(lotes.existePorCodigo((String) excedente.get("codigo")));
            assertEquals(1000, lotes.ocupacaoDaCamara(camaraId));
        });
    }

    @Test
    void protegeACapacidadeComDuasSolicitacoesSimultaneas() throws Exception {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var prontas = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);
        Callable<Response> requisicao = () -> {
            var body = bodyValido(camaraId, 600);
            prontas.countDown();
            assertTrue(inicio.await(10, TimeUnit.SECONDS));
            return cadastrar(body);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(requisicao);
            var segunda = executor.submit(requisicao);
            assertTrue(prontas.await(10, TimeUnit.SECONDS));
            inicio.countDown();
            var resposta1 = primeira.get(30, TimeUnit.SECONDS);
            var resposta2 = segunda.get(30, TimeUnit.SECONDS);

            assertEquals(List.of(201, 409), List.of(resposta1.statusCode(), resposta2.statusCode())
                    .stream().sorted().toList());
            verificarProblema(resposta1.statusCode() == 409 ? resposta1 : resposta2, 409, "capacidade-excedida");
        }
        QuarkusTransaction.requiringNew().run(() -> assertEquals(600, lotes.ocupacaoDaCamara(camaraId)));
    }

    @Test
    void recusaCamaraForaDeOperacao() {
        long camaraId = novaCamara(1000, EstadoCamara.MANUTENCAO);

        verificarProblema(cadastrar(bodyValido(camaraId, 10)), 409, "estado-incompativel");
    }

    @Test
    void recusaCamaraInativa() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        given().delete("/api/camaras/{id}", camaraId).then().statusCode(204);

        verificarProblema(cadastrar(bodyValido(camaraId, 10)), 409, "estado-incompativel");
    }

    @Test
    void camaraInexistenteRetorna404() {
        verificarProblema(cadastrar(bodyValido(Long.MAX_VALUE, 10)), 404, "recurso-nao-encontrado");
    }

    @Test
    void codigoRepetidoRetorna409() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        cadastrar(body).then().statusCode(201);

        verificarProblema(cadastrar(body), 409, "codigo-duplicado");
    }

    @Test
    void validadeNaoFuturaRetorna422() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put("validade", LocalDate.now(ZoneId.of("America/Fortaleza")).toString());

        verificarProblema(cadastrar(body), 422, "validacao").body("erros.campo", hasItem("validade"));
    }

    @Test
    void camposObrigatoriosERegrasDeEntradaRetornam422() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        for (var campo : List.of("codigo", "imunobiologico", "fabricante", "validade", "quantidade", "camaraId")) {
            var body = bodyValido(camaraId, 10);
            body.remove(campo);
            verificarProblema(cadastrar(body), 422, "validacao").body("erros.campo", hasItem(campo));
        }
        verificarProblema(cadastrar(bodyValido(camaraId, 0)), 422, "validacao")
                .body("erros.campo", hasItem("quantidade"));
        var codigoInvalido = bodyValido(camaraId, 10);
        codigoInvalido.put("codigo", "LOTE#1");
        verificarProblema(cadastrar(codigoInvalido), 422, "validacao").body("erros.campo", hasItem("codigo"));
        QuarkusTransaction.requiringNew().run(() -> assertEquals(0, lotes.ocupacaoDaCamara(camaraId)));
    }

    @Test
    void bodyAusenteRetorna422() {
        verificarProblema(given().contentType("application/json").post(ROTA), 422, "validacao")
                .body("erros.campo", hasItem("body"));
    }

    @Test
    void quantidadeDecimalRetorna400() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put("quantidade", 10.5);

        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
    }

    private long novaCamara(int capacidade, EstadoCamara estado) {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Câmara",
                "UBS teste", capacidade, new BigDecimal("2.0"), new BigDecimal("8.0"), estado,
                OffsetDateTime.now())).getId());
        camarasDoTeste.add(id);
        return id;
    }

    private static Map<String, Object> bodyValido(long camaraId, int quantidade) {
        return new HashMap<>(Map.of(
                "codigo", "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT),
                "imunobiologico", "Febre amarela",
                "fabricante", "Bio-Manguinhos",
                "validade", LocalDate.now(ZoneId.of("America/Fortaleza")).plusYears(1).toString(),
                "quantidade", quantidade,
                "camaraId", camaraId));
    }

    private static Response cadastrar(Object body) {
        return given().contentType("application/json").body(body).post(ROTA);
    }

    private static ValidatableResponse verificarProblema(Response resposta, int status, String tipo) {
        return resposta.then()
                .statusCode(status)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/" + tipo))
                .body("status", equalTo(status))
                .body("detail", not(blankOrNullString()))
                .body("instance", equalTo(ROTA));
    }
}
