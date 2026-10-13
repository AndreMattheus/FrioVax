package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LoteAtualizacaoHttpMemoriaTest extends LoteHttpMemoriaSupport {

    @Test
    void atualizaCamposEAumentaQuantidadeSemContarProprioLoteDuasVezes() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 600);
        var antes = consultar(id);
        var body = bodyValido(camaraId, 1000);
        body.put("imunobiologico", " Outra vacina ");
        body.put("codigo", "CODIGO-IGNORADO");

        var resposta = atualizar(id, body).then().statusCode(200).contentType("application/json")
                .body("codigo", equalTo(antes.jsonPath().getString("codigo")))
                .body("imunobiologico", equalTo("Outra vacina"))
                .body("quantidade", equalTo(1000))
                .body("camaraId", equalTo((int) camaraId))
                .body("criadoEm", equalTo(antes.jsonPath().getString("criadoEm")))
                .extract().jsonPath().getMap("");
        assertEquals(resposta, consultarLote(id));
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
        var antes = consultarLote(id);
        var body = bodyValido(destino, 400);
        body.put("fabricante", "Novo fabricante");

        problema(atualizar(id, body), 409, "capacidade-excedida", id);

        assertEquals(antes, consultarLote(id));
        assertEquals(400, ocupacao(origem));
        assertEquals(200, ocupacao(destino));
    }

    @Test
    void rejeitaReducaoEVencimentoSemAlterarLote() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 400);
        var antes = consultarLote(id);
        var body = bodyValido(camaraId, 399);
        problema(atualizar(id, body), 422, "validacao", id)
                .then().body("erros.campo", hasItem("quantidade"));

        body.put("quantidade", 400);
        body.put("validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).toString());
        problema(atualizar(id, body), 422, "validacao", id)
                .then().body("erros.campo", hasItem("validade"));

        assertEquals(antes, consultarLote(id));
    }

    @Test
    void validaBodyECamaraDeDestino() {
        long origem = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(origem, 100);
        var antes = consultarLote(id);
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

        assertEquals(antes, consultarLote(id));
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

    @ParameterizedTest
    @ValueSource(strings = {"imunobiologico", "fabricante", "validade", "quantidade", "camaraId"})
    void campoNuloRetorna422EPreservaTodosOsDados(String campo) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 100);
        var antes = consultarLote(id);
        var body = bodyValido(camaraId, 100);
        body.put(campo, null);
        problema(atualizar(id, body), 422, "validacao", id).then().body("erros.campo", hasItem(campo));
        assertEquals(antes, consultarLote(id));
        assertEquals(100, ocupacao(camaraId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ invalido", "[]", "{\"quantidade\": 10.0}", "{\"validade\":\"2027-02-30\"}"})
    void jsonInvalidoRetorna400SemAlterarLote(String body) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 100);
        var antes = consultarLote(id);
        problema(atualizar(id, body), 400, "requisicao-invalida", id);
        assertEquals(antes, consultarLote(id));
        assertEquals(100, ocupacao(camaraId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESGOTADO", "DESCARTADO"})
    void naoPermiteAumentarLoteEsgotadoOuDescartado(String estado) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long id = cadastrar(camaraId, 100);
        if (estado.equals("ESGOTADO")) {
            given().contentType("application/json").body(Map.of("quantidade", 100, "motivo", "ADMINISTRADA"))
                    .post("/api/lotes/{id}/baixas", id).then().statusCode(200);
        } else {
            given().post("/api/lotes/{id}/descarte", id).then().statusCode(200);
        }
        var antes = consultarLote(id);
        long ocupacaoAnterior = ocupacao(camaraId);
        problema(atualizar(id, bodyValido(camaraId, 101)), 409, "estado-incompativel", id);
        assertEquals(antes, consultarLote(id));
        assertEquals(ocupacaoAnterior, ocupacao(camaraId));
    }

    @Test
    void preservaValidadeVencidaMasRejeitaTrocaParaOutraDataPassada() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var hoje = LocalDate.now(HttpEmMemoriaProfile.RELOGIO);
        var agora = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
        // Prepara um lote que era válido quando cadastrado e venceu antes do relógio atual do teste.
        long id = lotes.salvar(Lote.novo("VENCIDO", "Vacina", "Fabricante",
                hoje.minusDays(1), 100, camaraId, hoje.minusDays(2), agora.minusDays(2))).getId();
        var body = bodyValido(camaraId, 100);
        body.put("validade", hoje.minusDays(1).toString());
        atualizar(id, body).then().statusCode(200).body("validade", equalTo(hoje.minusDays(1).toString()));
        var antes = consultarLote(id);

        body.put("validade", hoje.minusDays(2).toString());
        problema(atualizar(id, body), 422, "validacao", id).then().body("erros.campo", hasItem("validade"));
        assertEquals(antes, consultarLote(id));
        assertEquals(100, ocupacao(camaraId));
    }

    private long novaCamara(int capacidade, EstadoCamara estado) {
        return given().contentType("application/json")
                .body(Map.of("codigo", "C-" + UUID.randomUUID().toString().substring(0, 8),
                        "nome", "Câmara", "unidade", "UBS teste", "capacidade", capacidade,
                        "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", estado.name()))
                .post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
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
                "validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1).toString(),
                "quantidade", quantidade,
                "camaraId", camaraId));
    }

    private static Response atualizar(long id, Object body) {
        return given().contentType("application/json").queryParam("origem", "teste")
                .body(body).put("/api/lotes/{id}", id);
    }

    private static Response consultar(long id) {
        return given().get("/api/lotes/{id}", id).then().statusCode(200).extract().response();
    }

    private static int ocupacao(long camaraId) {
        return given().get("/api/camaras/{id}", camaraId).then().statusCode(200)
                .extract().jsonPath().getInt("ocupacao");
    }

    private static Response problema(Response resposta, int status, String tipo, long id) {
        problemaHttp(resposta, status, tipo, "/api/lotes/" + id);
        return resposta;
    }
}
