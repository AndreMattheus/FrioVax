package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class CamaraRoteamentoHttpMemoriaTest extends HttpEmMemoriaSupport {
    @Test
    void rotaInexistenteMantem404() {
        given().get("/api/rota-que-nao-existe").then().statusCode(404);
    }

    @Test
    void verboNaoSuportadoRetorna405ComProblemDetails() {
        given().queryParam("origem", "teste").patch("/api/camaras").then()
                .statusCode(405).contentType("application/problem+json")
                .body("status", equalTo(405),
                        "type", equalTo("/problemas/metodo-nao-permitido"),
                        "instance", equalTo("/api/camaras"));
    }
}
