package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/** Asserções comuns do contrato HTTP de lotes; não substitui os casos de uso. */
abstract class LoteHttpMemoriaSupport extends HttpEmMemoriaSupport {
    protected static ValidatableResponse problemaHttp(Response resposta, int status, String tipo, String caminho) {
        return resposta.then().statusCode(status).contentType("application/problem+json")
                .body("type", equalTo("/problemas/" + tipo), "status", equalTo(status),
                        "title", not(blankOrNullString()), "detail", not(blankOrNullString()),
                        "instance", equalTo(caminho));
    }

    protected static Map<String, Object> consultarLote(long id) {
        return given().get("/api/lotes/{id}", id).then().statusCode(200)
                .contentType("application/json").extract().jsonPath().getMap("");
    }

    protected static long ocupacaoDaCamara(long id) {
        return given().get("/api/camaras/{id}", id).then().statusCode(200)
                .extract().jsonPath().getLong("ocupacao");
    }
}
