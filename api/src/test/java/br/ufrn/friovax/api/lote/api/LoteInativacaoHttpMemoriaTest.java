package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LoteInativacaoHttpMemoriaTest extends LoteHttpMemoriaSupport {
    @Test
    void preservaRegistroEVinculoELiberaOcupacaoUmaUnicaVez() {
        long camaraId = given().contentType("application/json")
                .body(Map.of("codigo", "CAM-01", "nome", "Câmara", "unidade", "UBS teste", "capacidade", 1000,
                        "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL"))
                .post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
        long loteId = criarLote(camaraId, "LOTE-01", 120);
        long outroId = criarLote(camaraId, "LOTE-02", 30);
        var esperado = new HashMap<>(consultarLote(loteId));
        assertEquals(150, ocupacaoDaCamara(camaraId));

        given().delete("/api/lotes/{id}", loteId).then().statusCode(204).body(equalTo(""));

        esperado.put("ativo", false);
        assertEquals(esperado, consultarLote(loteId));
        assertEquals(30, ocupacaoDaCamara(camaraId));
        given().queryParam("camaraId", camaraId).get("/api/lotes").then().statusCode(200)
                .body("totalElements", equalTo(1), "items[0].id", equalTo((int) outroId));
        given().queryParam("camaraId", camaraId).queryParam("ativo", false).get("/api/lotes").then()
                .statusCode(200).body("totalElements", equalTo(1), "items[0].id", equalTo((int) loteId));

        given().delete("/api/lotes/{id}", loteId).then().statusCode(204).body(equalTo(""));
        assertEquals(esperado, consultarLote(loteId));
        assertEquals(30, ocupacaoDaCamara(camaraId));

        problemaHttp(given().contentType("application/json").body(body(camaraId, "LOTE-01", 120))
                .post("/api/lotes"), 409, "codigo-duplicado", "/api/lotes");
        assertEquals(esperado, consultarLote(loteId));
        assertEquals(30, ocupacaoDaCamara(camaraId));
    }

    @Test
    void inexistenteRetorna404ComProblemDetails() {
        problemaHttp(given().queryParam("origem", "teste").delete("/api/lotes/{id}", Long.MAX_VALUE),
                404, "recurso-nao-encontrado", "/api/lotes/" + Long.MAX_VALUE);
    }

    @Test
    void repetirInativacaoEmOutroHorarioPreservaDataAnterior() {
        var hoje = LocalDate.now(HttpEmMemoriaProfile.RELOGIO);
        var agora = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
        long camaraId = camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora.minusDays(2))).getId();
        var lote = Lote.novo("INATIVO-ONTEM", "Vacina", "Fabricante", hoje.plusYears(1),
                100, camaraId, hoje.minusDays(2), agora.minusDays(2));
        lote.inativar(agora.minusDays(1));
        long id = lotes.salvar(lote).getId();
        var antes = consultarLote(id);

        given().delete("/api/lotes/{id}", id).then().statusCode(204).body(equalTo(""));

        assertEquals(antes, consultarLote(id));
        assertEquals(0, ocupacaoDaCamara(camaraId));
    }

    private long criarLote(long camaraId, String codigo, int quantidade) {
        return given().contentType("application/json").body(body(camaraId, codigo, quantidade))
                .post("/api/lotes").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private Map<String, Object> body(long camaraId, String codigo, int quantidade) {
        return Map.of("codigo", codigo, "imunobiologico", "Vacina", "fabricante", "Fabricante",
                "validade", LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1).toString(),
                "quantidade", quantidade, "camaraId", camaraId);
    }
}
