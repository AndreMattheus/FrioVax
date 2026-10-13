package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LoteCadastroHttpMemoriaTest extends LoteHttpMemoriaSupport {
    private static final String ROTA = "/api/lotes";

    @Test
    void cadastraLoteComLocationECorpo() {
        long camaraId = novaCamara(5000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 1200);
        var codigo = (String) body.get("codigo");
        body.put("codigo", "  " + codigo.toLowerCase(Locale.ROOT) + "  ");

        var resposta = cadastrar(body);

        resposta.then().statusCode(201).contentType("application/json")
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
        assertNull(URI.create(resposta.header("Location")).getQuery());
        assertEquals(Set.of("id", "codigo", "imunobiologico", "fabricante", "validade", "quantidade",
                "camaraId", "estado", "ativo", "criadoEm", "atualizadoEm"), resposta.jsonPath().getMap("").keySet());
        assertEquals(OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO),
                OffsetDateTime.parse(resposta.jsonPath().getString("criadoEm")));
        assertEquals(resposta.jsonPath().getString("criadoEm"), resposta.jsonPath().getString("atualizadoEm"));
        assertEquals(resposta.jsonPath().getMap(""), given().get(resposta.header("Location"))
                .then().statusCode(200).extract().jsonPath().getMap(""));
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

        assertFalse(lotes.existePorCodigo((String) excedente.get("codigo")));
        assertEquals(1000, lotes.ocupacaoDaCamara(camaraId));
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void codigoRepetidoRetorna409InclusiveParaLoteInativo(boolean ativo) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        long id = cadastrar(body).then().statusCode(201).extract().jsonPath().getLong("id");
        if (!ativo) {
            given().delete("/api/lotes/{id}", id).then().statusCode(204);
        }
        var antes = consultarLote(id);
        body.put("codigo", " " + ((String) body.get("codigo")).toLowerCase(Locale.ROOT) + " ");

        verificarProblema(cadastrar(body), 409, "codigo-duplicado");
        assertEquals(antes, consultarLote(id));
        assertEquals(ativo ? 10 : 0, ocupacaoDaCamara(camaraId));
    }

    @Test
    void validadeNaoFuturaRetorna422() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put("validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).toString());

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
        assertEquals(0, lotes.ocupacaoDaCamara(camaraId));
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

    @ParameterizedTest
    @ValueSource(strings = {"codigo", "imunobiologico", "fabricante", "validade", "quantidade", "camaraId"})
    void campoNuloRetorna422SemGravar(String campo) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put(campo, null);
        verificarProblema(cadastrar(body), 422, "validacao").body("erros.campo", hasItem(campo));
        verificarSemGravacao(camaraId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ invalido", "{\"quantidade\":10", "[]"})
    void jsonInvalidoRetorna400(String body) {
        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        given().get(ROTA).then().statusCode(200).body("totalElements", equalTo(0));
    }

    @ParameterizedTest
    @CsvSource({"validade, 2027-02-30", "validade, 31/12/2027", "quantidade, texto", "camaraId, texto"})
    void tipoOuDataInvalidaRetorna400(String campo, String valor) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put(campo, valor);
        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        verificarSemGravacao(camaraId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0", "2147483648", "-2147483649"})
    void quantidadeForaDoTipoInteiroRetorna400(String quantidade) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put("quantidade", new BigDecimal(quantidade));
        verificarProblema(cadastrar(body), 400, "requisicao-invalida");
        verificarSemGravacao(camaraId);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void validadeUsaDataDeFortaleza(int dias) {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var body = bodyValido(camaraId, 10);
        body.put("validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusDays(dias).toString());
        var resposta = cadastrar(body);
        if (dias > 0) {
            // Neste instante, amanhã em Fortaleza ainda é hoje em UTC.
            resposta.then().statusCode(201).body("validade", equalTo(body.get("validade")));
        } else {
            verificarProblema(resposta, 422, "validacao").body("erros.campo", hasItem("validade"));
            verificarSemGravacao(camaraId);
        }
    }

    private void verificarSemGravacao(long camaraId) {
        given().get(ROTA).then().statusCode(200).body("totalElements", equalTo(0));
        given().queryParam("ativo", false).get(ROTA).then().statusCode(200).body("totalElements", equalTo(0));
        assertEquals(0, ocupacaoDaCamara(camaraId));
    }

    private long novaCamara(int capacidade, EstadoCamara estado) {
        return given().contentType("application/json")
                .body(Map.of("codigo", "C-" + UUID.randomUUID().toString().substring(0, 8),
                        "nome", "Câmara", "unidade", "UBS teste", "capacidade", capacidade,
                        "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", estado.name()))
                .post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> bodyValido(long camaraId, int quantidade) {
        return new HashMap<>(Map.of(
                "codigo", "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT),
                "imunobiologico", "Febre amarela",
                "fabricante", "Bio-Manguinhos",
                "validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1).toString(),
                "quantidade", quantidade,
                "camaraId", camaraId));
    }

    private static Response cadastrar(Object body) {
        return given().contentType("application/json").queryParam("origem", "teste").body(body).post(ROTA);
    }

    private static ValidatableResponse verificarProblema(Response resposta, int status, String tipo) {
        return problemaHttp(resposta, status, tipo, ROTA);
    }
}
