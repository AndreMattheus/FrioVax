package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LoteBaixaDescarteHttpMemoriaTest extends LoteHttpMemoriaSupport {
    private static final String BAIXAS = "/api/lotes/{id}/baixas";
    private static final String DESCARTE = "/api/lotes/{id}/descarte";

    private long camaraId;

    @BeforeEach
    void criarCamara() {
        camaraId = camaras.salvar(Camara.nova(
                "B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Câmara", "UBS teste", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO))).getId();
    }

    // ----- baixa -----

    @Test
    void baixaParcialRetorna200EReduzOcupacao() {
        long loteId = criarLote(100);
        var esperado = new HashMap<>(consultarLote(loteId));
        esperado.put("quantidade", 70);

        var resposta = baixa(loteId, 30, "ADMINISTRADA").then().statusCode(200).contentType("application/json")
                .body("id", equalTo((int) loteId), "quantidade", equalTo(70), "estado", equalTo("DISPONIVEL"))
                .extract().jsonPath().getMap("");
        assertEquals(esperado, resposta);
        assertEquals(esperado, consultarLote(loteId));

        given().get("/api/camaras/{id}", camaraId).then().statusCode(200).body("ocupacao", equalTo(70));
    }

    @Test
    void baixaDoRestanteEsgotaOLote() {
        long loteId = criarLote(40);

        baixa(loteId, 40, "PERDA").then().statusCode(200)
                .body("quantidade", equalTo(0), "estado", equalTo("ESGOTADO"));
        assertEquals(0, ocupacaoDaCamara(camaraId));
        var antes = consultarLote(loteId);
        problemaHttp(baixa(loteId, 1, "PERDA"), 422, "validacao", "/api/lotes/" + loteId + "/baixas");
        assertEquals(antes, consultarLote(loteId));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 41})
    void quantidadeForaDoIntervaloRetorna422SemAlterar(int quantidade) {
        long loteId = criarLote(40);
        var antes = consultarLote(loteId);

        problemaHttp(baixa(loteId, quantidade, "PERDA"), 422, "validacao", "/api/lotes/" + loteId + "/baixas")
                .body("erros.campo", hasItem("quantidade"));

        assertEquals(antes, consultarLote(loteId));
        assertEquals(40, ocupacaoDaCamara(camaraId));
    }

    @ParameterizedTest
    @CsvSource({"quantidade, false", "quantidade, true", "motivo, false", "motivo, true"})
    void campoAusenteOuNuloRetorna422(String campo, boolean nulo) {
        long loteId = criarLote(40);
        var antes = consultarLote(loteId);
        var body = new HashMap<String, Object>(Map.of("quantidade", 10, "motivo", "PERDA"));
        if (nulo) {
            body.put(campo, null);
        } else {
            body.remove(campo);
        }

        problemaHttp(given().contentType("application/json").body(body).post(BAIXAS, loteId),
                422, "validacao", "/api/lotes/" + loteId + "/baixas").body("erros.campo", hasItem(campo));
        assertEquals(antes, consultarLote(loteId));
        assertEquals(40, ocupacaoDaCamara(camaraId));
    }

    @Test
    void baixaEmLoteDescartadoRetorna409() {
        long loteId = criarLote(40);
        given().post(DESCARTE, loteId).then().statusCode(200);
        var antes = consultarLote(loteId);

        problemaHttp(baixa(loteId, 10, "PERDA"), 409, "estado-incompativel", "/api/lotes/" + loteId + "/baixas");

        assertEquals(antes, consultarLote(loteId));
        assertEquals(40, ocupacaoDaCamara(camaraId));
    }

    @Test
    void baixaEmLoteInexistenteRetorna404() {
        problemaHttp(baixa(Long.MAX_VALUE, 10, "PERDA"), 404, "recurso-nao-encontrado",
                "/api/lotes/" + Long.MAX_VALUE + "/baixas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ invalido", "[]", "{\"quantidade\":10.5,\"motivo\":\"PERDA\"}",
            "{\"quantidade\":10.0,\"motivo\":\"PERDA\"}", "{\"quantidade\":2147483648,\"motivo\":\"PERDA\"}",
            "{\"quantidade\":10,\"motivo\":\"INVALIDO\"}", "{\"quantidade\":10,\"motivo\":0}"})
    void jsonOuTiposInvalidosRetornam400SemAlteracao(String body) {
        long loteId = criarLote(40);
        var antes = consultarLote(loteId);
        problemaHttp(given().contentType("application/json").queryParam("origem", "teste")
                .body(body).post(BAIXAS, loteId), 400, "requisicao-invalida", "/api/lotes/" + loteId + "/baixas");
        assertEquals(antes, consultarLote(loteId));
        assertEquals(40, ocupacaoDaCamara(camaraId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null"})
    void bodyAusenteOuNuloRetorna422(String body) {
        long loteId = criarLote(40);
        var antes = consultarLote(loteId);
        problemaHttp(given().contentType("application/json").body(body).post(BAIXAS, loteId),
                422, "validacao", "/api/lotes/" + loteId + "/baixas").body("erros.campo", hasItem("body"));
        assertEquals(antes, consultarLote(loteId));
        assertEquals(40, ocupacaoDaCamara(camaraId));
    }

    @Test
    void baixaEmLoteInativoRetorna409SemAlteracao() {
        long loteId = criarLote(40);
        given().delete("/api/lotes/{id}", loteId).then().statusCode(204);
        var antes = consultarLote(loteId);
        problemaHttp(baixa(loteId, 10, "PERDA"), 409, "estado-incompativel", "/api/lotes/" + loteId + "/baixas");
        assertEquals(antes, consultarLote(loteId));
        assertEquals(0, ocupacaoDaCamara(camaraId));
    }

    // ----- descarte -----

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void descarteSemBodyComOuSemContentTypeMantemQuantidadeEOcupacao(boolean comContentType) {
        long loteId = criarLote(100);
        var esperado = new HashMap<>(consultarLote(loteId));
        esperado.put("estado", "DESCARTADO");
        var requisicao = given();
        if (comContentType) {
            requisicao.contentType("application/json");
        }

        var resposta = requisicao.post(DESCARTE, loteId).then().statusCode(200).contentType("application/json")
                .body("estado", equalTo("DESCARTADO"), "quantidade", equalTo(100), "ativo", equalTo(true))
                .extract().jsonPath().getMap("");
        assertEquals(esperado, resposta);
        assertEquals(esperado, consultarLote(loteId));

        // O lote descartado ocupa a câmara até ser inativado (D6).
        given().get("/api/camaras/{id}", camaraId).then().body("ocupacao", equalTo(100));
        given().delete("/api/lotes/{id}", loteId).then().statusCode(204);
        assertEquals(0, ocupacaoDaCamara(camaraId));
    }

    @Test
    void repetirODescarteRetorna200SemAlterar() {
        var hoje = LocalDate.now(HttpEmMemoriaProfile.RELOGIO);
        var agora = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
        var lote = Lote.novo("DESCARTADO-ONTEM", "Vacina", "Fabricante", hoje.plusYears(1),
                100, camaraId, hoje.minusDays(2), agora.minusDays(2));
        lote.descartar(agora.minusDays(1));
        long loteId = lotes.salvar(lote).getId();
        // O descarte anterior tem outro horário: redefinir atualizadoEm agora deve fazer o teste falhar.
        var anterior = consultarLote(loteId);

        given().post(DESCARTE, loteId).then().statusCode(200).body("estado", equalTo("DESCARTADO"));

        assertEquals(anterior, consultarLote(loteId));
        assertEquals(100, ocupacaoDaCamara(camaraId));
    }

    @Test
    void descarteEmLoteInativoRetorna409() {
        long loteId = criarLote(100);
        given().delete("/api/lotes/{id}", loteId).then().statusCode(204);
        var antes = consultarLote(loteId);

        problemaHttp(given().post(DESCARTE, loteId), 409, "estado-incompativel", "/api/lotes/" + loteId + "/descarte");
        assertEquals(antes, consultarLote(loteId));
        assertEquals(0, ocupacaoDaCamara(camaraId));
    }

    @Test
    void descarteEmLoteInexistenteRetorna404() {
        problemaHttp(given().queryParam("origem", "teste").post(DESCARTE, Long.MAX_VALUE),
                404, "recurso-nao-encontrado", "/api/lotes/" + Long.MAX_VALUE + "/descarte");
    }

    // ----- apoio -----

    private io.restassured.response.Response baixa(long loteId, int quantidade, String motivo) {
        return given().contentType("application/json").queryParam("origem", "teste")
                .body("{ \"quantidade\": %d, \"motivo\": \"%s\" }".formatted(quantidade, motivo))
                .when().post(BAIXAS, loteId);
    }

    private long criarLote(int quantidade) {
        return given().contentType("application/json")
                .body(Map.of("codigo", "L-" + UUID.randomUUID(), "imunobiologico", "Vacina",
                        "fabricante", "Fabricante", "validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1).toString(),
                        "quantidade", quantidade, "camaraId", camaraId))
                .post("/api/lotes").then().statusCode(201).extract().jsonPath().getLong("id");
    }

}
