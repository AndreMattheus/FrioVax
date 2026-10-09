package br.ufrn.friovax.api.compartilhado.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
class DocumentacaoApiHttpTest {

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
    void publicaSwaggerUi() {
        given().when().get("/q/swagger-ui/").then()
                .statusCode(200)
                .contentType(containsString("text/html"));
    }
}
