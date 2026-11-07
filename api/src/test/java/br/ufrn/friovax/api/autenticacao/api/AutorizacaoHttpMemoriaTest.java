package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport;
import br.ufrn.friovax.api.suporte.GuardsTesteResource;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class AutorizacaoHttpMemoriaTest extends HttpEmMemoriaSupport {
    private static final List<String> IDENTIDADES = List.of(
            "ANONIMO", "TOKEN_INVALIDO", "TOKEN_EXPIRADO", "SEM_PERFIL", "PERFIL_DESCONHECIDO",
            "ADMINISTRADOR", "OPERADOR", "AUDITOR");
    private long camaraId;
    private long camaraVaziaId;
    private long loteId;

    @BeforeEach
    void preparar() {
        camaraId = admin().body(camara("CAM-BASE")).post("/api/camaras")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        camaraVaziaId = admin().body(camara("CAM-VAZIA")).post("/api/camaras")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        loteId = admin().body(lote("LOT-BASE")).post("/api/lotes")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        GuardsTesteResource.EXECUCOES.set(0);
        GuardsTesteResource.CHAMADAS_GO.set(0);
    }

    static Stream<Arguments> rotas() {
        return Stream.of(
                new Rota("GET", "/api/camaras", "consulta", 200),
                new Rota("GET", "/api/camaras/{camara}", "consulta", 200),
                new Rota("POST", "/api/camaras", "cadastroCamara", 201),
                new Rota("PUT", "/api/camaras/{camara}", "cadastroCamara", 200),
                new Rota("DELETE", "/api/camaras/{vazia}", "cadastroCamara", 204),
                new Rota("GET", "/api/lotes", "consulta", 200),
                new Rota("GET", "/api/lotes/{lote}", "consulta", 200),
                new Rota("POST", "/api/lotes", "cadastroLote", 201),
                new Rota("PUT", "/api/lotes/{lote}", "cadastroLote", 200),
                new Rota("DELETE", "/api/lotes/{lote}", "cadastroLote", 204),
                new Rota("POST", "/api/lotes/{lote}/baixas", "baixa", 200),
                new Rota("POST", "/api/lotes/{lote}/descarte", "descarte", 200))
                .flatMap(rota -> IDENTIDADES.stream().map(identidade -> Arguments.of(rota, identidade)));
    }

    @ParameterizedTest(name = "{0} com {1}")
    @MethodSource("rotas")
    void aplicaMatrizEmTodasAsRotasSemGravarQuandoNegado(Rota rota, String identidade) {
        var antes = estado();
        String path = rota.path().replace("{camara}", Long.toString(camaraId))
                .replace("{vazia}", Long.toString(camaraVaziaId)).replace("{lote}", Long.toString(loteId));
        boolean permitido = identidade.equals("ADMINISTRADOR")
                || (rota.operacao().equals("consulta") && Set.of("OPERADOR", "AUDITOR").contains(identidade))
                || (rota.operacao().equals("baixa") && identidade.equals("OPERADOR"));
        int esperado = permitido ? rota.sucesso() : naoAutenticado(identidade) ? 401 : 403;
        var requisicao = como(identidade).contentType("application/json")
                .header("X-Perfil", "ADMINISTRADOR").header("X-Usuario-Id", "forjado")
                .queryParam("perfil", "ADMINISTRADOR").queryParam("ator", "forjado");
        if (Set.of("POST", "PUT").contains(rota.metodo())) {
            Map<String, Object> body = switch (rota.operacao()) {
                case "cadastroCamara" -> camara("CAM-NOVA");
                case "cadastroLote" -> lote("LOT-NOVO");
                case "baixa" -> new HashMap<>(Map.of("quantidade", 1, "motivo", "ADMINISTRADA"));
                default -> new HashMap<>();
            };
            body.put("perfil", "ADMINISTRADOR");
            body.put("ator", Map.of("id", "forjado", "perfil", "ADMINISTRADOR"));
            body.put("usuarioId", "forjado");
            requisicao.body(body);
        }
        requisicao.request(rota.metodo(), path).then().statusCode(esperado);
        if (!permitido) assertEquals(antes, estado(), "Requisição negada alterou os registros");
    }

    static Stream<Arguments> guards() {
        return Stream.of("leitura", "politica", "resolucao", "trilha", "sem-guarda")
                .flatMap(guarda -> IDENTIDADES.stream().map(id -> Arguments.of(guarda, id)));
    }

    @ParameterizedTest(name = "Guarda {0} com {1}")
    @MethodSource("guards")
    void reutilizaGuardsAntesDaExecucaoEDaChamadaExterna(String guarda, String identidade) {
        boolean permitido = !guarda.equals("sem-guarda") && (identidade.equals("ADMINISTRADOR")
                || (guarda.equals("leitura") && identidade.equals("OPERADOR"))
                || (guarda.equals("trilha") && identidade.equals("AUDITOR")));
        como(identidade).contentType("application/json")
                .body(Map.of("perfil", "ADMINISTRADOR", "ator", "administrador"))
                .request(guarda.equals("trilha") ? "GET" : "POST", "/teste/guards/" + guarda)
                .then().statusCode(permitido ? 204 : naoAutenticado(identidade) ? 401 : 403);
        assertEquals(permitido ? 1 : 0, GuardsTesteResource.EXECUCOES.get());
        assertEquals(permitido && guarda.equals("leitura") ? 1 : 0, GuardsTesteResource.CHAMADAS_GO.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/camaras", "/api/lotes"})
    void rejeitaAntesDeDesserializarBodyInvalido(String path) {
        given().contentType("application/json").body("{invalido").post(path).then().statusCode(401);
        como("OPERADOR").contentType("application/json").body("{invalido").post(path)
                .then().statusCode(403);
    }

    @Test
    void documentacaoPublicaNaoLiberaMutacoes() {
        for (var path : List.of("/api/status", "/q/health/live", "/q/health/ready",
                "/q/openapi?format=json", "/q/swagger-ui/")) {
            given().get(path).then().statusCode(200);
        }
        given().header("Referer", "http://localhost/q/swagger-ui/")
                .contentType("application/json").body(camara("CAM-BYPASS"))
                .post("/api/camaras").then().statusCode(401);
        assertEquals(2, admin().get("/api/camaras").jsonPath().getInt("totalElements"));
    }

    private Map<String, Object> estado() {
        return Map.of("camaras", admin().get("/api/camaras?ativo=true").asString(),
                "camarasInativas", admin().get("/api/camaras?ativo=false").asString(),
                "lotes", admin().get("/api/lotes?ativo=true").asString(),
                "lotesInativos", admin().get("/api/lotes?ativo=false").asString());
    }

    private RequestSpecification admin() {
        return AutenticacaoHttpSupport.given().contentType("application/json");
    }

    private RequestSpecification como(String identidade) {
        if (identidade.equals("ANONIMO")) return given();
        if (identidade.equals("TOKEN_INVALIDO")) return given().auth().oauth2("token-invalido");
        if (identidade.equals("TOKEN_EXPIRADO")) {
            var agora = Instant.now();
            return given().auth().oauth2(Jwt.issuer("friovax-api").subject("usuario-teste").upn("teste")
                    .groups(Set.of("ADMINISTRADOR")).issuedAt(agora.minusSeconds(901))
                    .expiresAt(agora.minusSeconds(1)).sign());
        }
        return given().auth().oauth2(AutenticacaoHttpSupport.token(
                identidade.equals("SEM_PERFIL") ? Set.of() : Set.of(identidade)));
    }

    private boolean naoAutenticado(String identidade) {
        return identidade.equals("ANONIMO") || identidade.startsWith("TOKEN_");
    }

    private Map<String, Object> camara(String codigo) {
        return new HashMap<>(Map.of("codigo", codigo, "nome", "Câmara", "unidade", "UBS",
                "capacidade", 100, "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL"));
    }

    private Map<String, Object> lote(String codigo) {
        return new HashMap<>(Map.of("codigo", codigo, "imunobiologico", "Vacina", "fabricante", "Fabricante",
                "validade", "2099-12-31", "quantidade", 10, "camaraId", camaraId));
    }

    record Rota(String metodo, String path, String operacao, int sucesso) {
        @Override public String toString() { return metodo + " " + path; }
    }
}
