package br.ufrn.friovax.api.compartilhado.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
class DocumentacaoApiHttpTest {

    @Test
    void publicaEspecificacaoOpenApiEmJson() {
        given().accept("application/json").when().get("/q/openapi").then()
                .statusCode(200)
                .contentType(containsString("application/json"))
                .body("info.title", equalTo("FrioVax API"),
                        "paths", hasKey("/api/camaras"),
                        "paths", hasKey("/api/lotes"),
                        "components.responses", hasKey("DadosInvalidos"));
    }

    @Test
    void publicaSwaggerUi() {
        given().when().get("/q/swagger-ui/").then()
                .statusCode(200)
                .contentType(containsString("text/html"));
    }
}
