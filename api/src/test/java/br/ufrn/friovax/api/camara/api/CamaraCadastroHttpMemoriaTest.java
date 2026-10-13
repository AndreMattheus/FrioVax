package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static io.restassured.config.HttpClientConfig.httpClientConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class CamaraCadastroHttpMemoriaTest extends HttpEmMemoriaSupport {

    private static final String ROTA = "/api/camaras";

    private final String codigo = "HTTP-" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 15).toUpperCase(Locale.ROOT);
    private final String unidade = "Teste HTTP " + UUID.randomUUID();

    private Map<String, Object> bodyValido() {
        return new HashMap<>(Map.of(
                "codigo", codigo,
                "nome", "Câmara principal",
                "unidade", unidade,
                "capacidade", 5000,
                "temperaturaMinima", new BigDecimal("2.0"),
                "temperaturaMaxima", new BigDecimal("8.0"),
                "estado", "OPERACIONAL"
        ));
    }

    private Response cadastrar(Object body) {
        return given()
                .config(config().httpClient(httpClientConfig()
                        .setParam("http.connection.timeout", 10000)
                        .setParam("http.socket.timeout", 10000)))
                .contentType("application/json")
                .body(body)
                .queryParam("origem", "teste")
                .post(ROTA);
    }

    private ValidatableResponse verificarProblema(Response resposta, int status, String tipo) {
        return resposta.then()
                .statusCode(status)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/" + tipo))
                .body("status", equalTo(status))
                .body("title", not(blankOrNullString()))
                .body("detail", not(blankOrNullString()))
                .body("instance", equalTo(ROTA));
    }

    private void verificarValidacao(Response resposta, String campo) {
        verificarProblema(resposta, 422, "validacao")
                .body("title", equalTo("Dados inválidos"))
                .body("erros.campo", hasItem(campo))
                .body("erros[0].mensagem", not(blankOrNullString()));
        assertEquals(0, contarRegistros());
    }

    private long contarRegistros() {
        return Stream.of(true, false).mapToLong(ativo ->
                given().queryParam("ativo", ativo).get(ROTA).then().statusCode(200)
                        .extract().jsonPath().getLong("totalElements")).sum();
    }

    private void verificarRepresentacaoSalva(Response resposta) {
        var consulta = given().get(resposta.header("Location")).then()
                .statusCode(200).contentType("application/json").extract().response();
        assertEquals(resposta.jsonPath().getMap(""), consulta.jsonPath().getMap(""));
        assertEquals(1, contarRegistros());
    }

    @Test
    void deveCadastrarCom201LocationRepresentacaoCompletaEConsulta() {
        var body = bodyValido();
        body.put("codigo", " " + codigo.toLowerCase(Locale.ROOT) + " ");
        body.put("nome", " Câmara principal ");
        body.put("unidade", " " + unidade + " ");
        body.put("temperaturaMinima", new BigDecimal("2.00"));
        body.put("temperaturaMaxima", 8);

        var resposta = given().contentType("application/json").queryParam("origem", "teste")
                .body(body).post(ROTA);
        resposta.then().statusCode(201).contentType("application/json")
                .body("id", greaterThan(0))
                .body("codigo", equalTo(codigo))
                .body("nome", equalTo("Câmara principal"))
                .body("unidade", equalTo(unidade))
                .body("capacidade", equalTo(5000))
                .body("temperaturaMinima", equalTo(2.0f))
                .body("temperaturaMaxima", equalTo(8.0f))
                .body("estado", equalTo("OPERACIONAL"))
                .body("ocupacao", equalTo(0))
                .body("ativo", equalTo(true));

        var json = resposta.jsonPath();
        assertEquals(Set.of("id", "codigo", "nome", "unidade", "capacidade", "temperaturaMinima",
                "temperaturaMaxima", "estado", "ocupacao", "ativo", "criadoEm", "atualizadoEm"),
                json.getMap("").keySet());
        var location = URI.create(resposta.header("Location"));
        assertEquals(ROTA + "/" + json.getLong("id"), location.getPath());
        assertNull(location.getQuery());
        var criadoEm = OffsetDateTime.parse(json.getString("criadoEm"));
        assertEquals(criadoEm, OffsetDateTime.parse(json.getString("atualizadoEm")));
        assertEquals(ZoneOffset.ofHours(-3), criadoEm.getOffset());
        assertEquals(OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO), criadoEm);
        verificarRepresentacaoSalva(resposta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERACIONAL", "MANUTENCAO", "DESATIVADA"})
    void deveAceitarTodosOsEstadosComCadastroAtivo(String estado) {
        var body = bodyValido();
        body.put("estado", estado);

        var resposta = cadastrar(body);

        resposta.then().statusCode(201).body("estado", equalTo(estado))
                .body("ativo", equalTo(true)).body("ocupacao", equalTo(0));
        verificarRepresentacaoSalva(resposta);
    }

    @Test
    void deveIgnorarCamposDesconhecidosESomenteLeitura() {
        var body = bodyValido();
        body.put("id", -1);
        body.put("ocupacao", 999);
        body.put("ativo", false);
        body.put("criadoEm", "2000-01-01T00:00:00Z");
        body.put("atualizadoEm", "2000-01-01T00:00:00Z");
        body.put("campoDesconhecido", "ignorado");

        var resposta = cadastrar(body);

        resposta.then().statusCode(201).body("id", greaterThan(0))
                .body("ocupacao", equalTo(0)).body("ativo", equalTo(true))
                .body("criadoEm", not(equalTo("2000-01-01T00:00:00Z")))
                .body("atualizadoEm", equalTo(resposta.jsonPath().getString("criadoEm")));
        assertFalse(resposta.jsonPath().getMap("").containsKey("campoDesconhecido"));
        verificarRepresentacaoSalva(resposta);
    }

    @Test
    void deveAceitarLimitesDeTextoTemperaturaECapacidade() {
        var body = bodyValido();
        var texto = "x".repeat(100);
        body.put("nome", " " + texto + " ");
        body.put("unidade", " " + texto + " ");
        body.put("capacidade", Integer.MAX_VALUE);
        body.put("temperaturaMinima", new BigDecimal("-999.90"));
        body.put("temperaturaMaxima", new BigDecimal("999.90"));

        var resposta = cadastrar(body);

        resposta.then().statusCode(201).body("nome", equalTo(texto)).body("unidade", equalTo(texto))
                .body("capacidade", equalTo(Integer.MAX_VALUE))
                .body("temperaturaMinima", equalTo(-999.9f)).body("temperaturaMaxima", equalTo(999.9f));
        verificarRepresentacaoSalva(resposta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"codigo", "nome", "unidade", "capacidade", "temperaturaMinima", "temperaturaMaxima", "estado"})
    void deveRejeitarCampoObrigatorioAusente(String campo) {
        var body = bodyValido();
        body.remove(campo);

        verificarValidacao(cadastrar(body), campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"codigo", "nome", "unidade", "capacidade", "temperaturaMinima", "temperaturaMaxima", "estado"})
    void deveRejeitarCampoObrigatorioNulo(String campo) {
        var body = bodyValido();
        body.put(campo, null);

        verificarValidacao(cadastrar(body), campo);
    }

    @ParameterizedTest
    @CsvSource({"codigo, ''", "codigo, '   '", "nome, ''", "nome, '   '", "unidade, ''", "unidade, '   '"})
    void deveRejeitarTextoObrigatorioEmBranco(String campo, String valor) {
        var body = bodyValido();
        body.put(campo, valor);

        verificarValidacao(cadastrar(body), campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "CAM_01", "CAMARA-COM-MAIS-DE-VINTE"})
    void deveRejeitarCodigoForaDoFormato(String valor) {
        var body = bodyValido();
        body.put("codigo", valor);

        verificarValidacao(cadastrar(body), "codigo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "unidade"})
    void deveRejeitarTextoLongoDepoisDoTrim(String campo) {
        var body = bodyValido();
        body.put(campo, " " + "x".repeat(101) + " ");

        verificarValidacao(cadastrar(body), campo);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarCapacidadeNaoPositiva(int capacidade) {
        var body = bodyValido();
        body.put("capacidade", capacidade);

        verificarValidacao(cadastrar(body), "capacidade");
    }

    @ParameterizedTest
    @CsvSource({
            "8.0, 8.0, temperaturaMinima",
            "9.0, 8.0, temperaturaMinima",
            "2.05, 8.0, temperaturaMinima",
            "2.0, 8.05, temperaturaMaxima",
            "-1000.0, 8.0, temperaturaMinima",
            "2.0, 1000.0, temperaturaMaxima"
    })
    void deveRejeitarFaixaTermicaInvalida(String minima, String maxima, String campo) {
        var body = bodyValido();
        body.put("temperaturaMinima", new BigDecimal(minima));
        body.put("temperaturaMaxima", new BigDecimal(maxima));

        verificarValidacao(cadastrar(body), campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.5", "10.0"})
    void deveRejeitarCapacidadeDecimalCom400(String capacidade) {
        var body = bodyValido();
        body.put("capacidade", new BigDecimal(capacidade));

        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        assertEquals(0, contarRegistros());
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, -2147483649L})
    void deveRejeitarCapacidadeForaDoInt32(long capacidade) {
        var body = bodyValido();
        body.put("capacidade", capacidade);

        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        assertEquals(0, contarRegistros());
    }

    @ParameterizedTest
    @CsvSource({"estado, INVALIDO", "capacidade, texto", "temperaturaMinima, texto"})
    void deveRejeitarValorIncompativelComOTipo(String campo, String valor) {
        var body = bodyValido();
        body.put(campo, valor);

        verificarProblema(cadastrar(body), 400, "requisicao-invalida")
                .body("detail", equalTo("O campo '" + campo + "' contém um valor incompatível com o tipo esperado."));
        assertEquals(0, contarRegistros());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void deveRejeitarEstadoNumerico(int estado) {
        var body = bodyValido();
        body.put("estado", estado);

        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        assertEquals(0, contarRegistros());
    }

    @Test
    void deveRejeitarJsonMalformado() {
        verificarProblema(cadastrar("{ isso não é json"), 400, "requisicao-invalida")
                .body("detail", equalTo("O body da requisição não é um JSON válido."));
        assertEquals(0, contarRegistros());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    void deveRejeitarBodyAusenteOuNulo(String body) {
        verificarValidacao(cadastrar(body), "body");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveRejeitarCodigoDuplicadoInclusiveDeCamaraInativa(boolean ativo) {
        var body = bodyValido();
        var criada = cadastrar(body);
        criada.then().statusCode(201);
        long id = criada.jsonPath().getLong("id");
        if (!ativo) {
            given().delete(ROTA + "/{id}", id).then().statusCode(204);
        }
        body.put("codigo", " " + codigo.toLowerCase(Locale.ROOT) + " ");
        body.put("nome", "Tentativa duplicada");

        verificarProblema(cadastrar(body), 409, "codigo-duplicado")
                .body("title", equalTo("Código duplicado"))
                .body("detail", equalTo("Já existe câmara com o código " + codigo + "."));

        assertEquals(1, contarRegistros());
        given().get(ROTA + "/{id}", id).then().statusCode(200)
                .body("nome", equalTo("Câmara principal"), "ativo", equalTo(ativo));
    }

}
