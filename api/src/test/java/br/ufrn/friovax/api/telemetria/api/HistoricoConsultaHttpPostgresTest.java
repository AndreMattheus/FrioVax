package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport;
import br.ufrn.friovax.api.telemetria.persistencia.HistoricoPostgresFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(HistoricoSemGoProfile.class)
class HistoricoConsultaHttpPostgresTest extends HistoricoPostgresFixture {
    @jakarta.inject.Inject AnaliseProibidaNoHistorico analise;

    @org.junit.jupiter.api.AfterEach
    void naoChamouAnalise() {
        assertEquals(0, analise.chamadas());
    }

    private String rotaLeituras() { return "/api/camaras/" + camara + "/leituras"; }

    @Test
    void paginaPadraoETemporaisComOffset() {
        given().get(rotaLeituras()).then().statusCode(200).body("page", equalTo(0), "size", equalTo(20),
                "totalElements", equalTo(4), "totalPages", equalTo(1), "items.size()", equalTo(4),
                "items[0].id", equalTo((int) ultimaLeitura), "items[0].instante", endsWith("Z"),
                "items[0].aceitaEm", endsWith("Z"));
        given().queryParam("page", 50).queryParam("size", 100).get(rotaLeituras()).then().statusCode(200)
                .body("items", empty(), "totalElements", equalTo(4), "totalPages", equalTo(1));
    }

    @Test
    void intervalosEquivalentesEmUtcEFortaleza() {
        var utc = given().queryParam("de", "2026-11-19T13:00:00Z").queryParam("ate", "2026-11-19T13:03:00Z")
                .get(rotaLeituras()).then().statusCode(200).body("totalElements", equalTo(3)).extract().asString();
        var local = given().queryParam("de", INICIO.toString()).queryParam("ate", INICIO.plusMinutes(3).toString())
                .get(rotaLeituras()).then().statusCode(200).extract().asString();
        assertEquals(utc, local);
        given().queryParam("de", INICIO.plusMinutes(3).toString()).get(rotaLeituras()).then().statusCode(200)
                .body("totalElements", equalTo(1));
        given().queryParam("ate", INICIO.toString()).get(rotaLeituras()).then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void parametrosInvalidosRetornamProblemDetails() {
        String[][] invalidos = {{"page", "-1"}, {"page", "abc"}, {"page", ""}, {"page", "2147483648"},
                {"size", "0"}, {"size", "101"}, {"size", "1.5"}, {"size", ""},
                {"de", "2026-11-19T10:00:00"}, {"ate", "ontem"}, {"de", ""}, {"ate", ""}};
        for (var parametro : invalidos) {
            for (String rota : new String[]{rotaLeituras(), "/api/ocorrencias"}) {
                given().queryParam(parametro[0], parametro[1]).get(rota).then().statusCode(400)
                        .contentType("application/problem+json");
            }
        }
        for (String ate : new String[]{"2026-11-19T13:00:00Z", "2026-11-19T12:00:00Z"}) {
            given().queryParam("de", INICIO.toString()).queryParam("ate", ate).get(rotaLeituras())
                    .then().statusCode(400);
        }
        for (String estado : new String[]{"NORMAL", "aberta", ""}) {
            given().queryParam("estado", estado).get("/api/ocorrencias").then().statusCode(400);
        }
        for (String id : new String[]{"0", "-1", "abc", ""}) {
            given().queryParam("camaraId", id).get("/api/ocorrencias").then().statusCode(400);
        }
    }

    @Test
    void ocorrenciasResumidasFiltradasEDetalheCompleto() {
        given().queryParam("camaraId", camara).queryParam("estado", "RESOLVIDA")
                .queryParam("de", INICIO.plusMinutes(2).toString()).queryParam("ate", INICIO.plusMinutes(3).toString())
                .get("/api/ocorrencias").then().statusCode(200).body("totalElements", equalTo(1),
                        "items[0].id", equalTo((int) resolvida), "items[0].resolvidaPor", equalTo(autor.toString()),
                        "items[0]", not(hasKey("evidencias")), "items[0]", not(hasKey("lotesAfetados")));
        given().get("/api/ocorrencias/" + resolvida).then().statusCode(200)
                .body("estado", equalTo("RESOLVIDA"), "evidencias.size()", equalTo(3),
                        "evidencias[0].id", equalTo((int) primeiraLeitura), "politica.versao", equalTo(1),
                        "resolvidaPor", equalTo(autor.toString()), "justificativa", equalTo("Inspeção concluída"),
                        "leituraResolucaoId", equalTo((int) ultimaLeitura), "abertaEm", endsWith("Z"),
                        "resolvidaEm", endsWith("Z"));
        given().get("/api/ocorrencias/" + aberta).then().statusCode(200).body("estado", equalTo("ABERTA"),
                "resolvidaPor", nullValue(), "resolvidaEm", nullValue(), "justificativa", nullValue());
    }

    @Test
    void ocorrenciasOrdenamEmpatesEUsamIntervaloDaAbertura() {
        var utc = given().queryParam("de", "2026-11-19T13:02:00Z").queryParam("ate", "2026-11-19T13:03:00Z")
                .queryParam("size", 1).get("/api/ocorrencias").then().statusCode(200)
                .body("items[0].id", equalTo((int) aberta), "totalElements", equalTo(2), "totalPages", equalTo(2))
                .extract().asString();
        var local = given().queryParam("de", INICIO.plusMinutes(2).toString())
                .queryParam("ate", INICIO.plusMinutes(3).toString()).queryParam("size", 1)
                .get("/api/ocorrencias").then().statusCode(200).extract().asString();
        assertEquals(utc, local);
        given().queryParam("de", INICIO.plusMinutes(2).toString()).queryParam("ate", INICIO.plusMinutes(3).toString())
                .queryParam("size", 1).queryParam("page", 1).get("/api/ocorrencias").then().statusCode(200)
                .body("items[0].id", equalTo((int) resolvida));
        given().queryParam("camaraId", camara).queryParam("ate", INICIO.plusMinutes(2).toString())
                .get("/api/ocorrencias").then().statusCode(200).body("items", empty());
        given().queryParam("camaraId", camara).queryParam("de", INICIO.plusMinutes(3).toString())
                .get("/api/ocorrencias").then().statusCode(200).body("items", empty());
    }

    @Test
    void mantemHistoricoAposAlterarPoliticaMoverEInativarLoteECamara() {
        alterarCadastros();
        given().get(rotaLeituras()).then().statusCode(200).body("totalElements", equalTo(4),
                "items[1].classificacao", equalTo("INSEGURA"), "items[1].politica.temperaturaMaxima", equalTo(8.0f));
        given().get("/api/ocorrencias/" + resolvida).then().statusCode(200)
                .body("camaraId", equalTo((int) camara), "politica.versao", equalTo(1),
                        "lotesAfetados[0].loteId", equalTo((int) lote), "lotesAfetados[0].quantidade", equalTo(100),
                        "lotesAfetados[0].imunobiologico", equalTo("Vacina"));
    }

    @Test
    void inexistentesEPaginasVazias() {
        given().get("/api/camaras/9223372036854775807/leituras").then().statusCode(404);
        given().get("/api/ocorrencias/9223372036854775807").then().statusCode(404);
        given().queryParam("camaraId", Long.MAX_VALUE).get("/api/ocorrencias").then().statusCode(200)
                .body("totalElements", equalTo(0), "items", empty());
        given().queryParam("camaraId", camara).queryParam("page", Integer.MAX_VALUE).queryParam("size", 100)
                .get("/api/ocorrencias").then().statusCode(200).body("totalElements", equalTo(1), "items", empty());
    }

    @Test
    void tresPerfisConsultamSemGoEIdentidadesSemPermissaoSaoNegadas() {
        for (String rota : new String[]{rotaLeituras(), "/api/ocorrencias", "/api/ocorrencias/" + resolvida}) {
            var antes = given().get(rota).then().statusCode(200).extract().asString();
            for (var perfil : Perfil.values()) {
                AutenticacaoHttpSupport.autenticado(perfil).get(rota).then().statusCode(200);
            }
            RestAssured.given().get(rota).then().statusCode(401);
            RestAssured.given().auth().oauth2("invalido").get(rota).then().statusCode(401);
            for (Set<String> grupos : java.util.List.of(Set.<String>of(), Set.of("DESCONHECIDO"))) {
                RestAssured.given().auth().oauth2(AutenticacaoHttpSupport.token(grupos)).get(rota).then().statusCode(403);
            }
            assertEquals(antes, given().get(rota).then().statusCode(200).extract().asString());
        }
    }

    @Test
    void openApiPublicaDescreveAsTresRotas() {
        var paths = RestAssured.given().accept("application/json").get("/q/openapi").then().statusCode(200)
                .extract().jsonPath().getMap("paths");
        assertTrue(paths.keySet().containsAll(Set.of("/api/camaras/{id}/leituras", "/api/ocorrencias", "/api/ocorrencias/{id}")));
        for (String rota : new String[]{"/api/camaras/{id}/leituras", "/api/ocorrencias", "/api/ocorrencias/{id}"}) {
            var operacao = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) paths.get(rota)).get("get");
            var respostas = (java.util.Map<?, ?>) operacao.get("responses");
            assertTrue(respostas.keySet().containsAll(Set.of("200", "400", "401", "403")));
            assertNotNull(operacao.get("security"));
        }
    }
}
