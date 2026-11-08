package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class DocumentacaoApiHttpMemoriaTest {

    @Test
    void todasAsOperacoesProtegidasDocumentamBearerPerfisE401403() {
        var spec = given().accept("application/json").get("/q/openapi").then()
                .statusCode(200).extract().jsonPath();
        Map<String, Map<String, Map<String, Object>>> paths = spec.getMap("paths");
        int verificadas = 0;
        for (var path : paths.entrySet()) {
            if (!path.getKey().startsWith("/api/camaras") && !path.getKey().startsWith("/api/lotes")) continue;
            for (var item : path.getValue().entrySet()) {
                if (!List.of("get", "post", "put", "delete").contains(item.getKey())) continue;
                verificadas++;
                var operacao = item.getValue();
                String rota = item.getKey() + " " + path.getKey();
                var security = (List<Map<String, Object>>) operacao.get("security");
                assertNotNull(security, rota);
                assertTrue(security.stream().anyMatch(s -> s.containsKey("bearerAuth")), rota);
                var respostas = (Map<String, Object>) operacao.get("responses");
                assertTrue(respostas.containsKey("401"), rota);
                assertTrue(respostas.containsKey("403"), rota);
                String descricao = operacao.get("description").toString();
                assertTrue(descricao.contains("ADMINISTRADOR"), rota);
                assertEquals(item.getKey().equals("get") || path.getKey().endsWith("/baixas"),
                        descricao.contains("OPERADOR"), rota);
                assertEquals(item.getKey().equals("get"), descricao.contains("AUDITOR"), rota);
            }
        }
        assertEquals(12, verificadas);
        assertFalse(paths.get("/api/status").get("get").containsKey("security"));
        for (var rota : List.of("login", "renovar", "logout")) {
            assertFalse(paths.get("/api/auth/" + rota).get("post").containsKey("security"));
        }
    }

    @Test
    void publicaEspecificacaoOpenApiEmJson() {
        given().accept("application/json").when().get("/q/openapi").then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("info.title", equalTo("FrioVax API"),
                "paths", hasKey("/api/camaras"),
                "paths", hasKey("/api/lotes"),
                "components.responses", hasKey("DadosInvalidos"));
    }

    @Test
    void descreveCamposEnumeracoesEDatasDosEsquemas() {
        given().accept("application/json").when().get("/q/openapi").then()
            .statusCode(200)
            .body("components.schemas.LoteRequest.required",
                hasItems("codigo", "imunobiologico", "fabricante", "validade", "quantidade", "camaraId"),
                "components.schemas.LoteRequest.properties.validade.description",
                containsString("America/Fortaleza"),
                "components.schemas.LocalDate.format", equalTo("date"),
                "components.schemas.OffsetDateTime.format", equalTo("date-time"),
                "components.schemas.EstadoCamara.enum", hasItems("OPERACIONAL", "MANUTENCAO", "DESATIVADA"),
                "components.schemas.EstadoLote.enum", hasItems("DISPONIVEL", "ESGOTADO", "DESCARTADO"),
                "components.schemas.EstadoTermico.enum",
                hasItems("SEM_LEITURAS", "NORMAL", "ATENCAO", "EM_ALERTA"),
                "components.schemas.CamaraResponse.properties.estadoTermico.description",
                containsString("independente do estado operacional"),
                "components.schemas.CamaraResponse.properties.ocupacao.description",
                containsString("lotes ativos"),
                "components.schemas.ProblemDetails.properties", hasKey("erros"),
                "components.schemas.ProblemDetails.properties.erros.type", hasItem("null"));
    }

    @Test
    void documentaRotasDeCamarasComRespostasDeSucessoEErro() {
        given().accept("application/json").when().get("/q/openapi").then()
            .statusCode(200)
            .body("paths.'/api/camaras'.post.responses", hasKey("201"),
                "paths.'/api/camaras'.post.responses.'201'.headers", hasKey("Location"),
                "paths.'/api/camaras'.post.responses.'409'.description",
                containsString("/problemas/codigo-duplicado"),
                "paths.'/api/camaras'.post.responses.'422'.'$ref'",
                equalTo("#/components/responses/DadosInvalidos"),
                "paths.'/api/camaras'.get.parameters.name",
                hasItems("unidade", "estado", "ativo", "page", "size"),
                "paths.'/api/camaras'.get.parameters.find { it.name == 'size' }.schema.maximum", equalTo(100),
                "paths.'/api/camaras/{id}'.put.responses.'409'.description",
                containsString("/problemas/capacidade-excedida"),
                "paths.'/api/camaras/{id}'.delete.responses", hasKey("204"),
                "paths.'/api/camaras/{id}'.delete.description", containsString("ativo=false"));
    }

    @Test
    void documentaRotasDeLotesComFiltrosEVinculoComCamara() {
        given().accept("application/json").when().get("/q/openapi").then()
            .statusCode(200)
            .body("paths.'/api/lotes'.post.responses", hasKey("201"),
                "paths.'/api/lotes'.post.responses.'404'.description", containsString("camaraId"),
                "paths.'/api/lotes'.post.responses.'409'.description",
                containsString("/problemas/capacidade-excedida"),
                "paths.'/api/lotes'.get.parameters.find { it.name == 'validadeDe' }.schema.format",
                equalTo("date"),
                "paths.'/api/lotes'.get.parameters.find { it.name == 'camaraId' }.schema.format",
                equalTo("int64"),
                "paths.'/api/lotes'.get.parameters.find { it.name == 'estado' }.schema.'$ref'",
                equalTo("#/components/schemas/EstadoLote"),
                "paths.'/api/lotes'.get.responses.'400'.description", containsString("validadeAte"),
                "paths.'/api/lotes/{id}'.put.description", containsString("baixa com motivo"),
                "paths.'/api/lotes/{id}'.delete.responses", hasKey("204"),
                "paths.'/api/status'.get.tags", hasItem("Status"));
    }

    @Test
    void documentaBaixaEDescarteDeLotes() {
        given().accept("application/json").when().get("/q/openapi").then()
            .statusCode(200)
            .body("components.schemas.BaixaLoteRequest.required", hasItems("quantidade", "motivo"),
                "components.schemas.MotivoBaixa.enum", hasItems("ADMINISTRADA", "PERDA"),
                "paths.'/api/lotes/{id}/baixas'.post.responses", hasKey("200"),
                "paths.'/api/lotes/{id}/baixas'.post.responses.'409'.description",
                containsString("/problemas/estado-incompativel"),
                "paths.'/api/lotes/{id}/baixas'.post.responses.'422'.'$ref'",
                equalTo("#/components/responses/DadosInvalidos"),
                "paths.'/api/lotes/{id}/descarte'.post.description", containsString("DELETE"),
                "paths.'/api/lotes/{id}/descarte'.post.responses", hasKey("200"));
    }

    @Test
    void publicaSwaggerUi() {
        given().when().get("/q/swagger-ui/").then()
            .statusCode(200)
            .contentType(containsString("text/html"));
    }
}
