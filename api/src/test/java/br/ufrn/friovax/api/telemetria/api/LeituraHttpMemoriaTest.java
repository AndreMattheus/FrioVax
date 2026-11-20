package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaDeTeste;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaResource;
import br.ufrn.friovax.api.telemetria.integracao.TelemetriaGrpcProfile;
import io.grpc.Status;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /api/leituras} com JWT real, repositórios em memória e um servidor gRPC de teste no lugar do Go.
 * Relógio fixo em 2026-10-11T01:30:00Z; câmara com faixa 2–8 °C e N=3.
 */
@QuarkusTest
@TestProfile(TelemetriaGrpcProfile.class)
class LeituraHttpMemoriaTest extends HttpEmMemoriaSupport {

    private static final OffsetDateTime AGORA = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);

    private ServidorTelemetriaDeTeste go;
    private long camaraId;

    @BeforeEach
    void preparar() {
        go = ServidorTelemetriaResource.servidor;
        go.reiniciarRegistros();
        camaraId = camaraNova("CAM-01", EstadoCamara.OPERACIONAL);
    }

    @Test
    void primeiraAceitacaoResponde201ComLocationEResultadoTermico() {
        var location = enviar(Perfil.OPERADOR, corpo("S-1", "9.4", "2026-10-10T22:20:00-03:00", "sensor-7"))
                .statusCode(201)
                .header("Location", endsWith("/api/leituras/1"))
                .body("id", equalTo(1),
                        "camaraId", equalTo((int) camaraId),
                        "idExterno", equalTo("S-1"),
                        "temperatura", equalTo(9.4f),
                        "medidaEm", equalTo("2026-10-11T01:20:00Z"),
                        "origem", equalTo("sensor-7"),
                        "politicaVersao", equalTo(1),
                        "classificacao", equalTo("INSEGURA"),
                        "sequencia", equalTo(1),
                        "limiarAtingido", equalTo(false))
                .extract().header("Location");

        autenticado(Perfil.AUDITOR).get(location).then().statusCode(200).body("idExterno", equalTo("S-1"));
        assertEquals(1, go.chamadas.get());
    }

    @Test
    void terceiraInseguraConsecutivaAtingeOLimiar() {
        enviar(corpo("S-1", "9.0", "2026-10-11T01:00:00Z", "s")).statusCode(201);
        enviar(corpo("S-2", "1.0", "2026-10-11T01:01:00Z", "s")).statusCode(201);

        enviar(corpo("S-3", "10.0", "2026-10-11T01:02:00Z", "s"))
                .statusCode(201)
                .body("sequencia", equalTo(3), "limiarAtingido", equalTo(true));

        var janela = go.ultimaRequisicao().getWindows(0);
        assertEquals(2, janela.getContextCount());
        assertEquals("S-3", janela.getCandidates(0).getExternalId());
    }

    @Test
    void reenvioAposPerderARespostaDevolve200ComOMesmoRegistroSemNovaAnalise() {
        enviar(corpo("S-1", "9.4", "2026-10-10T22:20:00-03:00", "sensor-7")).statusCode(201);

        enviar(corpo("S-1", "9.40", "2026-10-11T01:20:00Z", "  sensor-7 "))
                .statusCode(200)
                .header("Location", equalTo(null))
                .body("id", equalTo(1), "medidaEm", equalTo("2026-10-11T01:20:00Z"));

        assertEquals(1, go.chamadas.get());
        assertEquals(1, total());
    }

    @Test
    void mesmaChaveComOutroConteudoResponde409SemAlterarORegistro() {
        enviar(corpo("S-1", "9.4", "2026-10-11T01:20:00Z", "sensor-7")).statusCode(201);

        problema(enviar(corpo("S-1", "9.5", "2026-10-11T01:20:00Z", "sensor-7")), 409,
                "/problemas/leitura-conflitante");
        problema(enviar(corpo("S-1", "9.4", "2026-10-11T01:20:00.000001Z", "sensor-7")), 409,
                "/problemas/leitura-conflitante");
        problema(enviar(corpo("S-1", "9.4", "2026-10-11T01:20:00Z", "sensor-8")), 409,
                "/problemas/leitura-conflitante");

        assertEquals(1, go.chamadas.get());
        assertEquals(0, new BigDecimal("9.4").compareTo(leituras.buscarPorChave(camaraId, "S-1").orElseThrow()
                .getTemperatura()));
    }

    @Test
    void instanteForaDeOrdemOuCamaraIncompativelResponde409() {
        enviar(corpo("S-1", "5.0", "2026-10-11T01:20:00Z", "s")).statusCode(201);
        long manutencao = camaraNova("CAM-02", EstadoCamara.MANUTENCAO);

        problema(enviar(corpo("S-2", "5.0", "2026-10-11T01:20:00Z", "s")), 409, "/problemas/estado-incompativel");
        problema(enviar(corpo("S-3", "5.0", "2026-10-11T01:19:59Z", "s")), 409, "/problemas/estado-incompativel");
        problema(enviar(corpo(manutencao, "S-1", "5.0", "2026-10-11T01:20:00Z", "s")), 409,
                "/problemas/estado-incompativel");
        problema(enviar(corpo(999, "S-1", "5.0", "2026-10-11T01:20:00Z", "s")), 404,
                "/problemas/recurso-nao-encontrado");

        assertEquals(1, go.chamadas.get());
        assertEquals(1, total());
    }

    @Test
    void camposInvalidosRespondem400Ou422SemChamarOGo() {
        autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).body("{\"idExterno\": ").post("/api/leituras")
                .then().statusCode(400);
        enviar("{\"idExterno\":\"S-1\",\"camaraId\":1,\"temperatura\":\"quente\",\"medidaEm\":\"x\",\"origem\":\"s\"}")
                .statusCode(400);
        autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).post("/api/leituras")
                .then().statusCode(422).body("erros.campo", hasItem("body"));
        enviar("{}").statusCode(422)
                .body("erros.campo", hasItems("idExterno", "camaraId", "temperatura", "medidaEm", "origem"));
        campoInvalido(corpo("S-1", "8.05", "2026-10-11T01:20:00Z", "s"), "temperatura");
        campoInvalido(corpo("S-1", "1000.0", "2026-10-11T01:20:00Z", "s"), "temperatura");
        campoInvalido(corpo("x".repeat(101), "5.0", "2026-10-11T01:20:00Z", "s"), "idExterno");
        campoInvalido(corpo("S-1", "5.0", "2026-10-11T01:20:00Z", "   "), "origem");
        campoInvalido(corpo("S-1", "5.0", "2026-10-11T01:20:00.0000001Z", "s"), "medidaEm");

        assertEquals(0, go.chamadas.get());
    }

    @Test
    void medidaEmExigeOffsetExplicito() {
        campoInvalido(corpo("S-1", "5.0", "2026-10-11T01:20:00", "s"), "medidaEm");
        campoInvalido(corpo("S-1", "5.0", "2026-10-11", "s"), "medidaEm");
        enviar(corpo("S-1", "5.0", "2026-10-10T22:20:00-03:00", "s")).statusCode(201);
    }

    @Test
    void toleranciaFuturaDeCincoMinutosUsaORelogioFixo() {
        enviar(corpo("S-1", "5.0", AGORA.plusMinutes(5).toString(), "s")).statusCode(201);

        campoInvalido(corpo("S-2", "5.0", AGORA.plusMinutes(5).plusSeconds(1).toString(), "s"), "medidaEm");

        assertEquals(1, total());
    }

    @Test
    void operadorEAdministradorEnviamAuditorRecebe403ESemTokenRecebe401() {
        enviar(Perfil.OPERADOR, corpo("S-1", "5.0", "2026-10-11T01:00:00Z", "s")).statusCode(201);
        enviar(Perfil.ADMINISTRADOR, corpo("S-2", "5.0", "2026-10-11T01:01:00Z", "s")).statusCode(201);

        enviar(Perfil.AUDITOR, corpo("S-3", "5.0", "2026-10-11T01:02:00Z", "s")).statusCode(403);
        RestAssured.given().contentType(ContentType.JSON).body(corpo("S-3", "5.0", "2026-10-11T01:02:00Z", "s"))
                .post("/api/leituras").then().statusCode(401);
        RestAssured.get("/api/leituras/1").then().statusCode(401);
        autenticado(Perfil.AUDITOR).get("/api/leituras/999").then().statusCode(404);

        assertEquals(2, total());
    }

    @Test
    void goAusenteResponde503SemConfirmarLeitura() {
        go.comportarSe(ServidorTelemetriaDeTeste.falhando(Status.UNAVAILABLE));

        problema(enviar(corpo("S-1", "9.0", "2026-10-11T01:20:00Z", "s")), 503, "/problemas/analise-indisponivel");

        assertEquals(0, total());
    }

    @Test
    void goLentoResponde503DentroDoPrazoSemConfirmarLeitura() {
        go.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(5)));

        var inicio = System.nanoTime();
        problema(enviar(corpo("S-1", "9.0", "2026-10-11T01:20:00Z", "s")), 503, "/problemas/analise-indisponivel");
        var decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertTrue(decorrido.compareTo(Duration.ofSeconds(2)) < 0, "não esperou o Go lento: " + decorrido);
        assertEquals(0, total());
    }

    @Test
    void respostaForaDoContratoResponde502SemConfirmarLeitura() {
        go.comportarSe(ServidorTelemetriaDeTeste.analisando(
                resposta -> resposta.toBuilder().setCorrelationId("outra").build()));

        problema(enviar(corpo("S-1", "9.0", "2026-10-11T01:20:00Z", "s")), 502, "/problemas/falha-de-integracao");

        assertEquals(0, total());
    }

    private ValidatableResponse enviar(String corpo) {
        return enviar(Perfil.OPERADOR, corpo);
    }

    private static ValidatableResponse enviar(Perfil perfil, String corpo) {
        RequestSpecification requisicao = autenticado(perfil).contentType(ContentType.JSON).body(corpo);
        return requisicao.post("/api/leituras").then();
    }

    private String corpo(String idExterno, String temperatura, String medidaEm, String origem) {
        return corpo(camaraId, idExterno, temperatura, medidaEm, origem);
    }

    private static String corpo(long camaraId, String idExterno, String temperatura, String medidaEm, String origem) {
        return """
                {"idExterno": "%s", "camaraId": %d, "temperatura": %s, "medidaEm": "%s", "origem": "%s"}
                """.formatted(idExterno, camaraId, temperatura, medidaEm, origem);
    }

    private void campoInvalido(String corpo, String campo) {
        enviar(corpo).statusCode(422).contentType("application/problem+json")
                .body("type", equalTo("/problemas/validacao"), "erros.campo", hasItem(campo));
    }

    private static void problema(ValidatableResponse resposta, int status, String tipo) {
        resposta.statusCode(status).contentType("application/problem+json").body("type", equalTo(tipo));
    }

    private long camaraNova(String codigo, EstadoCamara estado) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), estado, AGORA.minusDays(1))).getId();
    }

    private long total() {
        return leituras.listarDaCamara(camaraId, new Paginacao(0, 100)).totalElementos();
    }
}
