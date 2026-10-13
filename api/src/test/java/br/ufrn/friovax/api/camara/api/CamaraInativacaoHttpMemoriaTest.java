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
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class CamaraInativacaoHttpMemoriaTest extends HttpEmMemoriaSupport {
    private static final String ROTA = "/api/camaras/{id}";

    @Test
    void retorna204SemCorpoEPreservaORegistro() {
        long id = criarCamara();
        given().when().delete(ROTA, id).then()
                .statusCode(204)
                .body(blankOrNullString());

        given().when().get(ROTA, id).then()
                .statusCode(200)
                .body("id", equalTo((int) id), "ativo", equalTo(false));
    }

    @Test
    void repetirDeleteNumaCamaraInativaTambemRetorna204() {
        long id = criarCamara();
        given().when().delete(ROTA, id).then().statusCode(204);
        String atualizadoEm = given().when().get(ROTA, id).then().extract().path("atualizadoEm");

        given().when().delete(ROTA, id).then()
                .statusCode(204)
                .body(blankOrNullString());

        given().when().get(ROTA, id).then()
                .body("ativo", equalTo(false), "atualizadoEm", equalTo(atualizadoEm));
    }

    @Test
    void retorna409ParaCamaraComLotesAtivos() {
        long id = criarCamara();
        criarLote(id);
        var anterior = given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap("");

        given().when().delete(ROTA, id).then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/estado-incompativel"),
                        "instance", equalTo("/api/camaras/" + id));

        assertEquals(anterior, given().get(ROTA, id).then().statusCode(200).extract().jsonPath().getMap(""));
    }

    @Test
    void retorna404ParaIdInexistente() {
        given().when().delete(ROTA, Long.MAX_VALUE).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/recurso-nao-encontrado"),
                        "instance", equalTo("/api/camaras/" + Long.MAX_VALUE));
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
