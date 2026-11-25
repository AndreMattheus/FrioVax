package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport;
import br.ufrn.friovax.api.suporte.CenarioDeOcorrencia;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code POST /api/ocorrencias/{id}/resolucao} com JWT real e repositórios em memória (S2-12). A ocorrência é montada
 * pelos repositórios, sem Go. Relógio fixo em 2026-10-11T01:30:00Z.
 */
@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class ResolucaoDeOcorrenciaHttpMemoriaTest extends HttpEmMemoriaSupport {

    private static final String RESOLUCAO = "/api/ocorrencias/{id}/resolucao";
    private static final OffsetDateTime AGORA = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private long camaraId;
    private CenarioDeOcorrencia cenario;
    private long ocorrenciaId;

    @BeforeEach
    void abrirOcorrencia() {
        camaraId = camaras.salvar(Camara.nova("CAM-RES", "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA.minusDays(1))).getId();
        cenario = new CenarioDeOcorrencia(camaras, leituras, ocorrencias, camaraId, AGORA.minusMinutes(30));
        ocorrenciaId = cenario.abrirOcorrencia();
    }

    @Test
    void administradorResolveComAutorEInstanteDoServidorIgnorandoCamposForjados() {
        long segura = cenario.segura().getId();
        var body = new HashMap<String, Object>(Map.of(
                "justificativa", "  Porta reparada; lotes avaliados  ",
                "resolvidaPor", bruno.toString(),
                "resolvidaEm", "2020-01-01T00:00:00Z",
                "leituraResolucaoId", 1,
                "autorId", bruno.toString()));

        como(ana).body(body).post(RESOLUCAO, ocorrenciaId).then()
                .statusCode(200)
                .body("id", equalTo((int) ocorrenciaId),
                        "estado", equalTo("RESOLVIDA"),
                        "resolvidaPor", equalTo(ana.toString()),
                        "resolvidaEm", equalTo("2026-10-11T01:30:00Z"),
                        "justificativa", equalTo("Porta reparada; lotes avaliados"),
                        "leituraResolucaoId", equalTo((int) segura),
                        "evidencias.size()", equalTo(3));

        autenticado(Perfil.AUDITOR).get("/api/ocorrencias/{id}", ocorrenciaId).then()
                .body("estado", equalTo("RESOLVIDA"), "resolvidaPor", equalTo(ana.toString()));
        autenticado(Perfil.OPERADOR).get("/api/camaras/{id}", camaraId).then()
                .body("estadoTermico", equalTo("NORMAL"));
        trilha().body("totalElements", equalTo(1),
                "items[0].operacao", equalTo("RESOLUCAO_DE_OCORRENCIA"),
                "items[0].ator.id", equalTo(ana.toString()),
                "items[0].ator.login", equalTo("ana"),
                "items[0].instante", equalTo("2026-10-11T01:30:00Z"),
                "items[0].descricao", containsString("resolvida após a leitura segura"));
    }

    @Test
    void operadorEAuditorRecebem403ESemTokenRecebe401SemAlterarNada() {
        cenario.segura();

        for (var perfil : new Perfil[] {Perfil.OPERADOR, Perfil.AUDITOR}) {
            autenticado(perfil).contentType(ContentType.JSON).body(Map.of("justificativa", "Porta reparada"))
                    .post(RESOLUCAO, ocorrenciaId).then().statusCode(403);
        }
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("justificativa", "Porta reparada"))
                .post(RESOLUCAO, ocorrenciaId).then().statusCode(401);

        continuaAberta();
    }

    @Test
    void semLeituraSeguraDepoisDaAberturaRetorna409() {
        resolver(ana, "Porta reparada").statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/resolucao-nao-permitida"),
                        "detail", containsString("nenhuma leitura foi aceita depois da abertura"));
        continuaAberta();
    }

    @Test
    void seguraAntigaSeguidaDeInseguraRetorna409() {
        cenario.segura();
        cenario.insegura(1, false);

        resolver(ana, "Porta reparada").statusCode(409)
                .body("type", equalTo("/problemas/resolucao-nao-permitida"), "detail", containsString("é insegura"));
        continuaAberta();
    }

    @Test
    void reenvioIdenticoDevolveOOriginalEConteudoDiferenteRetorna409() {
        cenario.segura();
        resolver(ana, "Porta reparada").statusCode(200);

        resolver(bruno, " Porta reparada ").statusCode(200)
                .body("resolvidaPor", equalTo(ana.toString()), "justificativa", equalTo("Porta reparada"));
        resolver(bruno, "Outra justificativa").statusCode(409)
                .body("type", equalTo("/problemas/resolucao-conflitante"));

        autenticado(Perfil.AUDITOR).get("/api/ocorrencias/{id}", ocorrenciaId).then()
                .body("resolvidaPor", equalTo(ana.toString()), "justificativa", equalTo("Porta reparada"));
        trilha().body("totalElements", equalTo(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"justificativa\":null}", "{\"justificativa\":\"   \"}", "", "null"})
    void justificativaAusenteOuEmBrancoRetorna422(String body) {
        cenario.segura();

        como(ana).body(body).post(RESOLUCAO, ocorrenciaId).then().statusCode(422)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/validacao"));
        continuaAberta();
    }

    @Test
    void justificativaLongaDemaisRetorna422NoCampo() {
        cenario.segura();

        resolver(ana, "x".repeat(501)).statusCode(422).body("erros.campo", contains("justificativa"));
        continuaAberta();
    }

    @Test
    void bodyMalFormadoRetorna400() {
        como(ana).body("{\"justificativa\":").post(RESOLUCAO, ocorrenciaId).then().statusCode(400);
        continuaAberta();
    }

    @Test
    void ocorrenciaInexistenteRetorna404() {
        como(ana).body(Map.of("justificativa", "Porta reparada")).post(RESOLUCAO, 999).then().statusCode(404)
                .body("type", equalTo("/problemas/recurso-nao-encontrado"));
    }

    private void continuaAberta() {
        autenticado(Perfil.AUDITOR).get("/api/ocorrencias/{id}", ocorrenciaId).then()
                .body("estado", equalTo("ABERTA"), "resolvidaPor", nullValue(), "justificativa", nullValue());
        autenticado(Perfil.AUDITOR).get("/api/camaras/{id}", camaraId).then()
                .body("estadoTermico", equalTo("EM_ALERTA"));
        trilha().body("totalElements", equalTo(0));
        assertEquals(1, ocorrencias.listarDaCamara(camaraId, Paginacao.padrao()).itens().size(),
                "nenhuma ocorrência a mais");
    }

    private ValidatableResponse trilha() {
        return autenticado(Perfil.AUDITOR)
                .get("/api/auditoria?recurso=OCORRENCIA&recursoId=" + ocorrenciaId).then().statusCode(200);
    }

    private ValidatableResponse resolver(UUID admin, String justificativa) {
        return como(admin).body(Map.of("justificativa", justificativa)).post(RESOLUCAO, ocorrenciaId).then();
    }

    private RequestSpecification como(UUID admin) {
        return AutenticacaoHttpSupport.como(admin, admin.equals(ana) ? "ana" : "bruno", Perfil.ADMINISTRADOR)
                .contentType(ContentType.JSON);
    }
}
