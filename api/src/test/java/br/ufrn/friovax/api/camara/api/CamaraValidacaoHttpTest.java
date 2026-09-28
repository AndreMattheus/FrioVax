package br.ufrn.friovax.api.camara.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CamaraValidacaoHttpTest {

    @Test
    void corpoInvalidoRetorna422ComProblemDetails() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "codigo": "CAM-01",
                  "nome": "Câmara 1",
                  "unidade": "UBS Centro",
                  "capacidade": -10,
                  "temperaturaMinima": 2.0,
                  "temperaturaMaxima": 8.0,
                  "estado": "OPERACIONAL"
                }
                """)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(422)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/validacao"));
    }

    @Test
    void jsonMalformadoRetorna400ComProblemDetails() {
        given()
            .contentType("application/json")
            .body("{ isso não é json")
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    void enumInvalidoNoJsonRetorna400ComProblemDetails() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "codigo": "CAM-01",
                  "nome": "Câmara 1",
                  "unidade": "UBS Centro",
                  "capacidade": 100,
                  "temperaturaMinima": 2.0,
                  "temperaturaMaxima": 8.0,
                  "estado": "ISSO_NAO_EXISTE"
                }
                """)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    void rotaInexistenteMantemStatus404EmVezDe500() {
        given()
        .when()
            .get("/testes/rota-que-nao-existe")
        .then()
            .statusCode(404);
    }
}
