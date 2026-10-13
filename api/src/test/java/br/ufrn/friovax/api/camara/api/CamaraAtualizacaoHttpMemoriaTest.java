package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class CamaraAtualizacaoHttpMemoriaTest extends HttpEmMemoriaSupport {
    private static final String ROTA = "/api/camaras/{id}";

    @Test
    void retorna200ComCamposAtualizadosEPreservaIdECodigo() {
        long id = criarCamara();
        var anterior = given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap("");
        String codigo = (String) anterior.get("codigo");
        var resposta = given().contentType("application/json")
            .body(corpo("Câmara reformada", 2000, "MANUTENCAO", "\"codigo\": \"OUTRO-CODIGO\","))
            .when().put(ROTA, id).then()
            .statusCode(200)
            .body("id", equalTo((int) id),
                "codigo", equalTo(codigo),
                "nome", equalTo("Câmara reformada"),
                "capacidade", equalTo(2000),
                "estado", equalTo("MANUTENCAO"),
                "criadoEm", equalTo(anterior.get("criadoEm")))
            .extract().jsonPath().getMap("");
        assertEquals(resposta, given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap(""));
    }

    @Test
    void retorna422ParaCorpoInvalidoSemAlterarNada() {
        long id = criarCamara();
        var anterior = given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap("");
        given().contentType("application/json")
            .body(corpo("Outro nome", 0, "OPERACIONAL", ""))
            .when().put(ROTA, id).then()
            .statusCode(422)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/validacao"));

        assertEquals(anterior, given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap(""));
    }

    @Test
    void retorna409AoReduzirCapacidadeAbaixoDaOcupacao() {
        long id = criarCamara();
        criarLote(id);
        var anterior = given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap("");

        given().contentType("application/json")
            .body(corpo("Câmara de teste", 50, "OPERACIONAL", ""))
            .when().put(ROTA, id).then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/capacidade-excedida"),
                "instance", equalTo("/api/camaras/" + id));

        assertEquals(anterior, given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap(""));
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
        return given().contentType("application/json")
                .body(Map.of("codigo", "CAM-01", "nome", "Câmara de teste",
                        "unidade", "UBS Centro", "capacidade", 5000,
                        "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL"))
                .post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private void criarLote(long camaraId) {
        lotes.salvar(Lote.novo("L-" + UUID.randomUUID().toString().substring(0, 8), "Vacina A", "Fabricante",
            LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1), 100, camaraId, LocalDate.now(HttpEmMemoriaProfile.RELOGIO), agora()));
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
    }

}
