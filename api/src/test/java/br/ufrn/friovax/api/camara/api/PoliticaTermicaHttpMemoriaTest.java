package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.como;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rotas de política térmica (US08, S2-14) sem banco: contrato, validação, versão esperada e trilha. */
@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class PoliticaTermicaHttpMemoriaTest extends HttpEmMemoriaSupport {
    private static final String POLITICA = "/api/camaras/{id}/politica-termica";
    private static final String VERSOES = POLITICA + "/versoes";

    private final UUID adminId = UUID.randomUUID();
    private long id;

    @BeforeEach
    void cadastrarCamara() {
        id = admin().body(Map.of("codigo", "CAM-POL", "nome", "Câmara", "unidade", "UBS", "capacidade", 100,
                        "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL"))
                .post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
    }

    @Test
    void consultaAVersaoVigenteDaCamaraNova() {
        autenticado(Perfil.OPERADOR).get(POLITICA, id).then().statusCode(200)
                .body("camaraId", equalTo((int) id), "versao", equalTo(1), "temperaturaMinima", equalTo(2.0f),
                        "temperaturaMaxima", equalTo(8.0f), "limiar", equalTo(3), "autorId", nullValue());
    }

    @Test
    void alteracaoCriaVersaoComAutorDoTokenEIgnoraAutorDoBody() {
        var body = new HashMap<>(politica(1, "3.0", "7.0", 5));
        body.put("autorId", UUID.randomUUID().toString());
        body.put("versao", 99);
        admin().body(body).put(POLITICA, id).then().statusCode(200)
                .body("versao", equalTo(2), "temperaturaMinima", equalTo(3.0f), "temperaturaMaxima", equalTo(7.0f),
                        "limiar", equalTo(5), "autorId", equalTo(adminId.toString()));

        autenticado(Perfil.AUDITOR).get(POLITICA, id).then().body("versao", equalTo(2));
        autenticado(Perfil.AUDITOR).get(VERSOES, id).then().statusCode(200)
                .body("items.versao", contains(2, 1), "items[1].temperaturaMinima", equalTo(2.0f),
                        "items[1].autorId", nullValue(), "totalElements", equalTo(2));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "POLITICA_TERMICA").queryParam("recursoId", id)
                .get("/api/auditoria").then()
                .body("items.operacao", contains("ALTERACAO_DE_POLITICA"),
                        "items[0].ator.id", equalTo(adminId.toString()),
                        "items[0].descricao", equalTo("Política térmica da câmara CAM-POL passou da versão 1 "
                                + "para a 2: faixa 2.0 a 8.0 °C → 3.0 a 7.0 °C; limiar 3 → 5"));
    }

    @Test
    void mesmosValoresRespondem200SemVersaoNemEvento() {
        int eventos = auditoria.todos().size();
        admin().body(politica(1, "2", "8.00", 3)).put(POLITICA, id).then().statusCode(200)
                .body("versao", equalTo(1));

        autenticado(Perfil.AUDITOR).get(VERSOES, id).then().body("totalElements", equalTo(1));
        assertEquals(eventos, auditoria.todos().size());
    }

    @Test
    void versaoEsperadaDesatualizadaRetorna409SemGravar() {
        admin().body(politica(1, "3.0", "7.0", 3)).put(POLITICA, id).then().statusCode(200);
        int eventos = auditoria.todos().size();

        admin().body(politica(1, "1.0", "9.0", 3)).put(POLITICA, id).then().statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/versao-desatualizada"), "status", equalTo(409));
        admin().body(politica(3, "1.0", "9.0", 3)).put(POLITICA, id).then().statusCode(409);

        autenticado(Perfil.AUDITOR).get(POLITICA, id).then().body("versao", equalTo(2),
                "temperaturaMinima", equalTo(3.0f));
        assertEquals(eventos, auditoria.todos().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null",
        "{\"temperaturaMinima\": 2, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 0, \"temperaturaMinima\": 2, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 2, \"temperaturaMaxima\": 8}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 8, \"temperaturaMaxima\": 2, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 8, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 2.05, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": -1000, \"temperaturaMaxima\": 8, \"limiar\": 3}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 2, \"temperaturaMaxima\": 8, \"limiar\": 0}",
        "{\"versaoEsperada\": 1, \"temperaturaMinima\": 2, \"temperaturaMaxima\": 8, \"limiar\": 101}"})
    void bodyAusenteOuDadosInvalidosRetornam422SemVersao(String body) {
        admin().body(body).put(POLITICA, id).then().statusCode(422).contentType("application/problem+json");
        autenticado(Perfil.AUDITOR).get(VERSOES, id).then().body("totalElements", equalTo(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalido", "[]", "{\"versaoEsperada\": \"um\", \"temperaturaMinima\": 2, "
            + "\"temperaturaMaxima\": 8, \"limiar\": 3}"})
    void bodyMalFormadoRetorna400(String body) {
        admin().body(body).put(POLITICA, id).then().statusCode(400);
        autenticado(Perfil.AUDITOR).get(VERSOES, id).then().body("totalElements", equalTo(1));
    }

    @Test
    void camaraInexistenteRetorna404() {
        admin().get(POLITICA, 999_999).then().statusCode(404);
        admin().get(VERSOES, 999_999).then().statusCode(404);
        admin().body(politica(1, "3.0", "7.0", 3)).put(POLITICA, 999_999).then().statusCode(404);
    }

    @Test
    void camaraInativaContinuaConsultavelMasNaoAceitaAlteracao() {
        admin().delete("/api/camaras/{id}", id).then().statusCode(204);

        admin().body(politica(1, "3.0", "7.0", 3)).put(POLITICA, id).then().statusCode(409)
                .body("type", equalTo("/problemas/estado-incompativel"));
        autenticado(Perfil.AUDITOR).get(POLITICA, id).then().statusCode(200).body("versao", equalTo(1));
        autenticado(Perfil.AUDITOR).get(VERSOES, id).then().statusCode(200).body("totalElements", equalTo(1));
    }

    @Test
    void historicoPaginaDaVersaoMaisRecente() {
        admin().body(politica(1, "3.0", "7.0", 3)).put(POLITICA, id).then().statusCode(200);
        admin().body(politica(2, "3.0", "7.0", 4)).put(POLITICA, id).then().statusCode(200);

        autenticado(Perfil.OPERADOR).queryParam("size", 2).get(VERSOES, id).then().statusCode(200)
                .body("items.versao", contains(3, 2), "totalElements", equalTo(3), "totalPages", equalTo(2));
        autenticado(Perfil.OPERADOR).queryParam("size", 2).queryParam("page", 1).get(VERSOES, id).then()
                .body("items.versao", contains(1));
        autenticado(Perfil.OPERADOR).queryParam("page", 9).get(VERSOES, id).then().statusCode(200)
                .body("items.size()", equalTo(0), "totalElements", equalTo(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=", "page=abc", "size=0", "size=101", "size="})
    void paginacaoInvalidaDoHistoricoRetorna400(String consulta) {
        autenticado(Perfil.AUDITOR).urlEncodingEnabled(false).get("/api/camaras/" + id
                + "/politica-termica/versoes?" + consulta).then().statusCode(400);
    }

    @Test
    void putDaCamaraComVersaoEsperadaSegueAMesmaRegra() {
        admin().body(politica(1, "3.0", "7.0", 5)).put(POLITICA, id).then().statusCode(200);

        // Quem leu a versão 1 não volta a faixa antiga pelo PUT da câmara.
        admin().body(camara("2.0", "8.0", 1)).put("/api/camaras/{id}", id).then().statusCode(409)
                .body("type", equalTo("/problemas/versao-desatualizada"));
        autenticado(Perfil.AUDITOR).get(POLITICA, id).then().body("versao", equalTo(2));

        admin().body(camara("2.5", "7.0", 2)).put("/api/camaras/{id}", id).then().statusCode(200)
                .body("temperaturaMinima", equalTo(2.5f));
        autenticado(Perfil.AUDITOR).get(POLITICA, id).then()
                .body("versao", equalTo(3), "limiar", equalTo(5), "autorId", equalTo(adminId.toString()));
        admin().body(camara("2.5", "7.0", 0)).put("/api/camaras/{id}", id).then().statusCode(422);
    }

    private RequestSpecification admin() {
        return como(adminId, "admin-politica", Perfil.ADMINISTRADOR).contentType("application/json");
    }

    private static Map<String, Object> politica(int versao, String minima, String maxima, int limiar) {
        return Map.of("versaoEsperada", versao, "temperaturaMinima", Double.parseDouble(minima),
                "temperaturaMaxima", Double.parseDouble(maxima), "limiar", limiar);
    }

    private static Map<String, Object> camara(String minima, String maxima, int versaoEsperada) {
        return Map.of("nome", "Câmara", "unidade", "UBS", "capacidade", 100,
                "temperaturaMinima", Double.parseDouble(minima), "temperaturaMaxima", Double.parseDouble(maxima),
                "estado", "OPERACIONAL", "versaoEsperada", versaoEsperada);
    }
}
