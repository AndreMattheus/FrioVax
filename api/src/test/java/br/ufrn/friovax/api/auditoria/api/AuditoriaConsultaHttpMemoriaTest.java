package br.ufrn.friovax.api.auditoria.api;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class AuditoriaConsultaHttpMemoriaTest extends HttpEmMemoriaSupport {
    private static final String TRILHA = "/api/auditoria";
    private static final String INSTANTE = "2026-10-11T01:30:00Z";

    private final UUID adminId = UUID.randomUUID();
    private long camaraId;

    @BeforeEach
    void cadastrarCamara() {
        camaraId = como(adminId, "admin-trilha", Perfil.ADMINISTRADOR).header("X-Correlation-Id", "corr-cadastro-1")
                .body(camara("CAM-TRILHA")).post("/api/camaras")
                .then().statusCode(201).header("X-Correlation-Id", "corr-cadastro-1")
                .extract().jsonPath().getLong("id");
    }

    @Test
    void auditorVeQuemQuandoOQueEACorrelacaoDoCadastro() {
        autenticado(Perfil.AUDITOR).get(TRILHA).then().statusCode(200)
                .body("totalElements", equalTo(1),
                        "items[0].recurso", equalTo("CAMARA"),
                        "items[0].recursoId", equalTo((int) camaraId),
                        "items[0].operacao", equalTo("CADASTRO"),
                        "items[0].descricao", startsWith("Câmara CAM-TRILHA cadastrada com capacidade 100 e faixa 2"),
                        "items[0].instante", equalTo(INSTANTE),
                        "items[0].ator.id", equalTo(adminId.toString()),
                        "items[0].ator.login", equalTo("admin-trilha"),
                        "items[0].ator.perfil", equalTo("ADMINISTRADOR"),
                        "items[0].correlacaoId", equalTo("corr-cadastro-1"));
    }

    @Test
    void atorVemDoTokenENaoDoBodyNemDosCabecalhos() {
        var body = new HashMap<>(Map.<String, Object>of("nome", "Câmara renomeada", "unidade", "UBS",
                "capacidade", 100, "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL"));
        body.put("ator", Map.of("id", UUID.randomUUID().toString(), "login", "forjado", "perfil", "SISTEMA"));
        body.put("usuarioId", UUID.randomUUID().toString());
        body.put("perfil", "AUDITOR");
        var outroAdmin = UUID.randomUUID();
        como(outroAdmin, "admin-real", Perfil.ADMINISTRADOR)
                .header("X-Usuario-Id", UUID.randomUUID().toString()).header("X-Perfil", "SISTEMA")
                .body(body).put("/api/camaras/{id}", camaraId).then().statusCode(200);

        autenticado(Perfil.AUDITOR).queryParam("usuarioId", outroAdmin.toString()).get(TRILHA).then()
                .body("totalElements", equalTo(1),
                        "items[0].operacao", equalTo("ATUALIZACAO"),
                        "items[0].ator.login", equalTo("admin-real"),
                        "items[0].ator.perfil", equalTo("ADMINISTRADOR"));
    }

    @Test
    void correlacaoForaDoFormatoETrocadaPelaDoServidor() {
        var resposta = como(adminId, "admin-trilha", Perfil.ADMINISTRADOR)
                .header("X-Correlation-Id", "com espaço e ; ponto e vírgula").body(camara("CAM-CORR"))
                .post("/api/camaras").then().statusCode(201).extract();
        String gerada = resposta.header("X-Correlation-Id");
        assertNotEquals("com espaço e ; ponto e vírgula", gerada);
        UUID.fromString(gerada);

        autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA")
                .queryParam("recursoId", resposta.jsonPath().getLong("id")).get(TRILHA).then()
                .body("items.correlacaoId", contains(gerada));
    }

    @Test
    void filtraPorPeriodoInclusivoUsuarioERecurso() {
        long loteId = como(adminId, "admin-trilha", Perfil.ADMINISTRADOR).body(lote("LOT-TRILHA"))
                .post("/api/lotes").then().statusCode(201).extract().jsonPath().getLong("id");
        var auditor = autenticado(Perfil.AUDITOR);

        auditor.queryParam("de", INSTANTE).queryParam("ate", INSTANTE).get(TRILHA).then()
                .body("totalElements", equalTo(2));
        autenticado(Perfil.AUDITOR).queryParam("de", "2026-10-11T01:30:00.000001Z").get(TRILHA).then()
                .body("totalElements", equalTo(0));
        // Mesmo instante com outro offset, e com o "+" sem codificação, que chega como espaço.
        autenticado(Perfil.AUDITOR).urlEncodingEnabled(false).queryParam("ate", "2026-10-11T04:30:00+03:00")
                .get(TRILHA).then().statusCode(200).body("totalElements", equalTo(2));
        autenticado(Perfil.AUDITOR).queryParam("ate", "2026-10-10T22:29:59.999999-03:00").get(TRILHA).then()
                .body("totalElements", equalTo(0));

        autenticado(Perfil.AUDITOR).queryParam("usuarioId", adminId.toString()).get(TRILHA).then()
                .body("totalElements", equalTo(2));
        autenticado(Perfil.AUDITOR).queryParam("usuarioId", UUID.randomUUID().toString()).get(TRILHA).then()
                .body("totalElements", equalTo(0));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "LOTE").get(TRILHA).then()
                .body("items.recursoId", contains((int) loteId));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA").queryParam("recursoId", camaraId)
                .get(TRILHA).then().body("items.operacao", contains("CADASTRO"));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "LOTE").queryParam("recursoId", camaraId + 1000)
                .get(TRILHA).then().statusCode(200).body("totalElements", equalTo(0));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "").queryParam("usuarioId", " ").get(TRILHA).then()
                .statusCode(200).body("totalElements", equalTo(2));
    }

    @Test
    void paginaPorIdCrescente() {
        como(adminId, "admin-trilha", Perfil.ADMINISTRADOR).body(camara("CAM-TRILHA-2")).post("/api/camaras")
                .then().statusCode(201);
        autenticado(Perfil.ADMINISTRADOR).queryParam("size", 1).queryParam("page", 1).get(TRILHA).then()
                .statusCode(200).body("page", equalTo(1), "size", equalTo(1), "totalElements", equalTo(2),
                        "totalPages", equalTo(2), "items[0].descricao", startsWith("Câmara CAM-TRILHA-2 cadastrada"));
        autenticado(Perfil.ADMINISTRADOR).queryParam("page", 5).get(TRILHA).then().statusCode(200)
                .body("items.size()", equalTo(0), "totalElements", equalTo(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"de=ontem", "de=2026-10-11T01:30:00", "ate=2026-13-01T00:00:00Z",
            "de=2026-10-12T00:00:00Z&ate=2026-10-11T00:00:00Z", "usuarioId=nao-e-uuid", "usuarioId=1-2-3-4-5",
            "recurso=camara", "recurso=USUARIO", "recursoId=1", "recurso=CAMARA&recursoId=0",
            "recurso=CAMARA&recursoId=abc", "page=-1", "page=", "size=0", "size=101", "size=", "size=dez"})
    void parametroInvalidoRetorna400(String consulta) {
        autenticado(Perfil.AUDITOR).urlEncodingEnabled(false).get(TRILHA + "?" + consulta).then()
                .statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("/problemas/requisicao-invalida"));
    }

    @Test
    void consultarATrilhaNaoGeraEvento() {
        for (int i = 0; i < 3; i++) {
            autenticado(Perfil.ADMINISTRADOR).get(TRILHA).then().statusCode(200);
        }
        assertEquals(1, auditoria.todos().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void trilhaNaoTemRotaDeEscrita(String metodo) {
        autenticado(Perfil.ADMINISTRADOR).contentType("application/json").body(Map.of("descricao", "forjado"))
                .request(metodo, TRILHA).then().statusCode(405);
        autenticado(Perfil.ADMINISTRADOR).contentType("application/json").body(Map.of("descricao", "forjado"))
                .request(metodo, TRILHA + "/1").then().statusCode(404);
        assertEquals(1, auditoria.todos().size());
    }

    @Test
    void operadorNaoConsultaATrilha() {
        autenticado(Perfil.OPERADOR).get(TRILHA).then().statusCode(403);
        RestAssured.given().get(TRILHA).then().statusCode(401);
    }

    @Test
    void eventoDoSistemaApareceSemIdDeUsuario() {
        // Fora de uma requisição autenticada o ator é SISTEMA, sem UUID (ContextoDaOperacaoHttp).
        auditoria.registrar(EventoDeAuditoria.novo(OffsetDateTime.parse(INSTANTE), Ator.SISTEMA,
                RecursoAuditado.CAMARA, camaraId, OperacaoAuditada.ATUALIZACAO, "Ajuste do sistema", "sistema-1"));
        autenticado(Perfil.AUDITOR).queryParam("size", 1).queryParam("page", 1).get(TRILHA).then()
                .body("items[0].ator.id", nullValue(), "items[0].ator.perfil", equalTo("SISTEMA"));
    }

    private static RequestSpecification como(UUID id, String login, Perfil perfil) {
        var agora = Instant.now();
        return RestAssured.given().contentType("application/json").auth().oauth2(Jwt.issuer("friovax-api")
                .subject(id.toString()).upn(login).groups(Set.of(perfil.name()))
                .issuedAt(agora).expiresAt(agora.plusSeconds(900)).sign());
    }

    private static Map<String, Object> camara(String codigo) {
        return Map.of("codigo", codigo, "nome", "Câmara", "unidade", "UBS", "capacidade", 100,
                "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL");
    }

    private Map<String, Object> lote(String codigo) {
        return Map.of("codigo", codigo, "imunobiologico", "Vacina", "fabricante", "Fabricante",
                "validade", "2099-12-31", "quantidade", 10, "camaraId", camaraId);
    }
}
