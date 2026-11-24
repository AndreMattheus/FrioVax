package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rotas de lote com a câmara {@code EM_ALERTA} (S2-11): 409 para entrada e uso de doses, o resto continua. */
@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LotesEmAlertaHttpMemoriaTest extends LoteHttpMemoriaSupport {
    private long emAlerta;
    private long normal;
    private long lote;

    @BeforeEach
    void preparar() {
        emAlerta = camara("A-");
        normal = camara("N-");
        lote = criar("L-ALERTA-" + UUID.randomUUID().toString().substring(0, 6), 100, emAlerta);
        var camara = camaras.buscarPorId(emAlerta).orElseThrow();
        camara.registrarResultadoTermico(true, true);
        camaras.salvar(camara);
    }

    @Test
    void novoLoteAumentoEMovimentacaoParaACamaraEmAlertaRetornam409() {
        problemaHttp(given().contentType("application/json").body(corpo("L-NOVO", 10, emAlerta))
                .post("/api/lotes"), 409, "estado-incompativel", "/api/lotes")
                .body("detail", containsString("EM_ALERTA"));

        problemaHttp(given().contentType("application/json").body(edicao(150, emAlerta))
                .put("/api/lotes/{id}", lote), 409, "estado-incompativel", "/api/lotes/" + lote);

        long outro = criar("L-FORA-" + UUID.randomUUID().toString().substring(0, 6), 10, normal);
        problemaHttp(given().contentType("application/json").body(edicao(10, emAlerta))
                .put("/api/lotes/{id}", outro), 409, "estado-incompativel", "/api/lotes/" + outro);

        assertEquals(100, consultarLote(lote).get("quantidade"));
        assertEquals((int) normal, consultarLote(outro).get("camaraId"));
    }

    @Test
    void baixaAdministradaRetorna409SemReduzirDoses() {
        problemaHttp(given().contentType("application/json").body(Map.of("quantidade", 5, "motivo", "ADMINISTRADA"))
                .post("/api/lotes/{id}/baixas", lote), 409, "estado-incompativel", "/api/lotes/" + lote + "/baixas");
        assertEquals(100, consultarLote(lote).get("quantidade"));
    }

    @Test
    void perdaDescarteERetiradaContinuamPermitidos() {
        given().contentType("application/json").body(Map.of("quantidade", 5, "motivo", "PERDA"))
                .post("/api/lotes/{id}/baixas", lote).then().statusCode(200).body("quantidade", equalTo(95));
        given().contentType("application/json").body(edicao(95, normal))
                .put("/api/lotes/{id}", lote).then().statusCode(200).body("camaraId", equalTo((int) normal));
        given().post("/api/lotes/{id}/descarte", lote).then().statusCode(200).body("estado", equalTo("DESCARTADO"));
    }

    private long camara(String prefixo) {
        return camaras.salvar(Camara.nova(prefixo + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Câmara", "UBS teste", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL, OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO))).getId();
    }

    private long criar(String codigo, int quantidade, long camaraId) {
        return given().contentType("application/json").body(corpo(codigo, quantidade, camaraId))
                .post("/api/lotes").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> corpo(String codigo, int quantidade, long camaraId) {
        var corpo = new HashMap<>(edicao(quantidade, camaraId));
        corpo.put("codigo", codigo);
        return corpo;
    }

    private static Map<String, Object> edicao(int quantidade, long camaraId) {
        return Map.of("imunobiologico", "Febre amarela", "fabricante", "Bio-Manguinhos", "validade", "2099-12-31",
                "quantidade", quantidade, "camaraId", camaraId);
    }
}
