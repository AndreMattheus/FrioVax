package br.ufrn.friovax.api.camara.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CamaraValidacaoHttpTest {

    private static Map<String, Object> bodyValido() {
        return new HashMap<>(Map.of(
                "codigo", "CAM-01",
                "nome", "Câmara 1",
                "unidade", "UBS Centro",
                "capacidade", 100,
                "temperaturaMinima", new BigDecimal("2.0"),
                "temperaturaMaxima", new BigDecimal("8.0"),
                "estado", "OPERACIONAL"
        ));
    }

    @Test
    void bodyInvalidoRetorna422ComProblemDetails() {
        var body = bodyValido();
        body.put("capacidade", -10);

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(422)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/validacao"))
            .body("status", equalTo(422))
            .body("instance", equalTo("/testes/camaras"))
            .body("erros[0].campo", equalTo("capacidade"))
            .body("erros[0].mensagem", equalTo("deve ser maior que zero"));
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
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/requisicao-invalida"))
            .body("status", equalTo(400))
            .body("title", equalTo("Requisição inválida"))
            .body("detail", equalTo("O body da requisição não é um JSON válido."))
            .body("instance", equalTo("/testes/camaras"));
    }

    @Test
    void enumInvalidoNoJsonRetorna400ComProblemDetails() {
        var body = bodyValido();
        body.put("estado", "ISSO_NAO_EXISTE");

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/requisicao-invalida"))
            .body("status", equalTo(400))
            .body("instance", equalTo("/testes/camaras"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.5", "10.0"})
    void capacidadeDecimalRetorna400SemConversaoParaInteiro(String capacidade) {
        var body = bodyValido();
        body.put("capacidade", new BigDecimal(capacidade));

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/requisicao-invalida"))
            .body("status", equalTo(400))
            .body("instance", equalTo("/testes/camaras"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void estadoNumericoRetorna400SemConversaoParaEnum(int estado) {
        var body = bodyValido();
        body.put("estado", estado);

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/requisicao-invalida"))
            .body("status", equalTo(400))
            .body("instance", equalTo("/testes/camaras"));
    }

    @ParameterizedTest
    @CsvSource({"-1000.0, 8.0, temperaturaMinima", "2.0, 1000.0, temperaturaMaxima",
            "2.05, 8.0, temperaturaMinima", "2.0, 8.05, temperaturaMaxima"})
    void temperaturaInvalidaNoDominioRetorna422(String minima, String maxima, String campo) {
        var body = bodyValido();
        body.put("temperaturaMinima", new BigDecimal(minima));
        body.put("temperaturaMaxima", new BigDecimal(maxima));

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(422)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/validacao"))
            .body("status", equalTo(422))
            .body("instance", equalTo("/testes/camaras"))
            .body("erros[0].campo", equalTo(campo));
    }

    @Test
    void aceitaTextosNoLimiteAposTrimETemperaturasNosLimites() {
        var body = bodyValido();
        var texto = "x".repeat(100);
        body.put("codigo", " cam-01 ");
        body.put("nome", " " + texto + " ");
        body.put("unidade", " " + texto + " ");
        body.put("temperaturaMinima", new BigDecimal("-999.90"));
        body.put("temperaturaMaxima", new BigDecimal("999.90"));
        body.put("id", 999);
        body.put("campoDesconhecido", "ignorado");

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(200)
            .body("codigo", equalTo("CAM-01"))
            .body("nome", equalTo(texto))
            .body("unidade", equalTo(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "unidade"})
    void textoAcimaDoLimiteAposTrimRetorna422(String campo) {
        var body = bodyValido();
        body.put(campo, " " + "x".repeat(101) + " ");

        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/testes/camaras")
        .then()
            .statusCode(422)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/validacao"))
            .body("instance", equalTo("/testes/camaras"))
            .body("erros[0].campo", equalTo(campo))
            .body("erros[0].mensagem", equalTo("deve ter no máximo 100 caracteres"));
    }

    @Test
    void conflitoDeDominioRetorna409ComInstanceSemQueryString() {
        given()
            .queryParam("origem", "teste")
        .when()
            .get("/testes/camaras/codigo-duplicado")
        .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/codigo-duplicado"))
            .body("status", equalTo(409))
            .body("instance", equalTo("/testes/camaras/codigo-duplicado"));
    }

    @Test
    void recursoNaoEncontradoRetorna404ComInstance() {
        given()
        .when()
            .get("/testes/camaras/nao-encontrada")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("type", equalTo("/problemas/recurso-nao-encontrado"))
            .body("status", equalTo(404))
            .body("instance", equalTo("/testes/camaras/nao-encontrada"));
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
