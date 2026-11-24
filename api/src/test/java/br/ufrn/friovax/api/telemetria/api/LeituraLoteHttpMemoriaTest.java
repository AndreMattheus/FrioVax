package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaSupport;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaDeTeste;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaResource;
import br.ufrn.friovax.api.telemetria.integracao.TelemetriaGrpcProfile;
import br.ufrn.friovax.telemetry.v1.Reading;
import br.ufrn.friovax.telemetry.v1.WindowError;
import br.ufrn.friovax.telemetry.v1.WindowErrorCode;
import io.grpc.Status;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(TelemetriaGrpcProfile.class)
class LeituraLoteHttpMemoriaTest extends HttpEmMemoriaSupport {

    private static final String ROTA = "/api/leituras/lote";
    private static final String CORRELACAO = "recepcao-lote-teste";
    private static final OffsetDateTime AGORA = OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
    private static final Paginacao PAGINA = new Paginacao(0, 20);

    private ServidorTelemetriaDeTeste go;
    private long camara;

    @BeforeEach
    void preparar() {
        go = ServidorTelemetriaResource.servidor;
        go.reiniciarRegistros();
        camara = novaCamara("LOTE-01");
    }

    @Test
    void analisaDuasCamarasEmUmaRpcEConservaCadaResultadoNaOrdemHttp() {
        long outra = novaCamara("LOTE-02");
        var entradas = lote(leitura(camara, "segura", -6, 4), leitura(outra, "a", -9, 4),
                leitura(camara, "c", -7, 9), leitura(camara, "a", -9, 9), leitura(camara, "b", -8, 9));

        var resultado = enviar(Perfil.OPERADOR, entradas).statusCode(200)
                .header("X-Correlation-Id", CORRELACAO)
                .body("idExterno", contains("segura", "a", "c", "a", "b"),
                        "camaraId", contains((int) camara, (int) outra, (int) camara, (int) camara, (int) camara),
                        "codigo", contains(201, 201, 201, 201, 201),
                        "situacao", contains("ACEITA", "ACEITA", "ACEITA", "ACEITA", "ACEITA"),
                        "sequencia", contains(0, 0, 3, 1, 2),
                        "limiarAtingido", contains(false, false, true, false, false))
                .extract().jsonPath();
        assertEquals(1, go.chamadas.get());
        var requisicao = go.ultimaRequisicao();
        assertEquals(CORRELACAO, requisicao.getCorrelationId());
        assertEquals(List.of(CORRELACAO), go.correlacoesNoMetadado);
        assertEquals(2, requisicao.getWindowsCount());
        assertEquals(List.of("a", "b", "c", "segura"), requisicao.getWindows(0).getCandidatesList().stream()
                .map(Reading::getExternalId).toList());
        var ocorrencia = ocorrencias.abertaDaCamara(camara).orElseThrow();
        assertEquals(resultado.getLong("[2].ocorrenciaId"), ocorrencia.getId());
        assertEquals(List.of(resultado.getLong("[3].id"), resultado.getLong("[4].id"), resultado.getLong("[2].id")),
                ocorrencia.getEvidencias());
        assertEquals(EstadoTermico.EM_ALERTA, camaras.buscarPorId(camara).orElseThrow().getEstadoTermico());

        enviar(Perfil.ADMINISTRADOR, entradas).statusCode(200)
                .body("codigo", contains(200, 200, 200, 200, 200),
                        "situacao", contains("REPETIDA", "REPETIDA", "REPETIDA", "REPETIDA", "REPETIDA"),
                        "id", equalTo(resultado.getList("id")),
                        "ocorrenciaId", equalTo(resultado.getList("ocorrenciaId")));
        assertEquals(1, go.chamadas.get());
        assertEquals(4, total(camara));
        assertEquals(1, total(outra));
        var eventos = auditoria.listar(FiltroAuditoria.nenhum(), PAGINA).itens();
        assertEquals(1, eventos.size());
        assertEquals(OperacaoAuditada.ABERTURA_DE_OCORRENCIA, eventos.getFirst().operacao());
        assertEquals(CORRELACAO, eventos.getFirst().correlacaoId());
        assertEquals("OPERADOR", eventos.getFirst().ator().perfil());

        enviar(lote(leitura(camara, "d", -5, 9), leitura(camara, "e", -4, 9), leitura(camara, "f", -3, 9)))
                .statusCode(200)
                .body("sequencia", contains(1, 2, 3), "[2].ocorrenciaId", nullValue());
        assertEquals(1, ocorrencias.listarDaCamara(camara, PAGINA).totalElementos());
        assertEquals(1, auditoria.listar(FiltroAuditoria.nenhum(), PAGINA).totalElementos());
    }

    @Test
    void usaContextoUmaVezEPreservaReenviosDeCamaraInativa() {
        long inativa = novaCamara("LOTE-02");
        var anterior = leitura(inativa, "antiga", -10, 4);
        var primeira = leitura(camara, "a", -10, 9);
        var segunda = leitura(camara, "b", -9, 9);
        enviar(lote(anterior, primeira, segunda)).statusCode(200)
                .body("sequencia", contains(0, 1, 2));
        var desativada = camaras.buscarPorId(inativa).orElseThrow();
        desativada.inativar(false, AGORA);
        camaras.salvar(desativada);

        enviar(lote(anterior, leitura(inativa, "nova", -8, 8), segunda, leitura(camara, "c", -8, 9)))
                .statusCode(200)
                .body("codigo", contains(200, 409, 200, 201),
                        "[1].motivo", equalTo("CAMARA_INAPTA"),
                        "[3].sequencia", equalTo(3));
        var janela = go.ultimaRequisicao().getWindows(0);
        assertEquals(1, go.ultimaRequisicao().getWindowsCount());
        assertEquals(List.of("a", "b"), janela.getContextList().stream().map(Reading::getExternalId).toList());
        assertEquals(List.of("c"), janela.getCandidatesList().stream().map(Reading::getExternalId).toList());
        assertEquals(3, total(camara));
        assertEquals(1, total(inativa));
        assertEquals(3, ocorrencias.abertaDaCamara(camara).orElseThrow().getEvidencias().size());
    }

    @Test
    void rejeitaChaveEmConflitoEDemaisNovasDaCamaraMesmoComRepetidaValida() {
        var primeira = leitura(camara, "a", -10, 4);
        var segunda = leitura(camara, "b", -9, 4);
        enviar(lote(primeira, segunda)).statusCode(200);

        enviar(lote(primeira, leitura(camara, "b", -9, 5), leitura(camara, "c", -8, 4))).statusCode(200)
                .body("codigo", contains(200, 409, 409),
                        "motivo", contains(null, "CHAVE_CONFLITANTE", "CHAVE_CONFLITANTE"));
        assertEquals(2, total(camara));
        assertEquals(1, go.chamadas.get());
    }

    @Test
    void rejeitaInstantesIguaisOuAntigosEIdentificaCamaraInexistenteSemRpc() {
        var anterior = leitura(camara, "a", -10, 4);
        enviar(lote(anterior)).statusCode(200);
        enviar(lote(anterior, leitura(camara, "b", -11, 4), leitura(camara, "c", -8, 4),
                leitura(999L, "c", -8, 4))).statusCode(200)
                .body("codigo", contains(200, 409, 409, 404),
                        "motivo", contains(null, "FORA_DE_ORDEM", "FORA_DE_ORDEM", "CAMARA_INEXISTENTE"));
        enviar(lote(leitura(camara, "d", -7, 4), leitura(camara, "e", -7, 4))).statusCode(200)
                .body("codigo", contains(409, 409), "motivo", contains("FORA_DE_ORDEM", "FORA_DE_ORDEM"));
        assertEquals(1, total(camara));
        assertEquals(1, go.chamadas.get());
    }

    @Test
    void falhaDeUmaJanelaNaRespostaRpcPreservaResultadoRepetidoEConfirmaOutraCamara() {
        long outra = novaCamara("LOTE-02");
        var anterior = leitura(camara, "a", -10, 4);
        enviar(lote(anterior)).statusCode(200);
        go.comportarSe(ServidorTelemetriaDeTeste.analisando(resposta -> resposta.toBuilder()
                .setResults(0, resposta.getResults(0).toBuilder().setError(WindowError.newBuilder()
                        .setCode(WindowErrorCode.WINDOW_ERROR_CODE_INVALID_WINDOW)
                        .setDescription("detalhe interno do Go")))
                .build()));

        enviar(lote(anterior, leitura(camara, "b", -9, 4), leitura(outra, "b", -9, 4))).statusCode(200)
                .body("codigo", contains(200, 502, 201),
                        "[1].motivo", equalTo("ANALISE_REJEITADA"),
                        "[1].id", nullValue());
        assertEquals(2, go.chamadas.get());
        assertEquals(1, total(camara));
        assertEquals(1, total(outra));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAVAILABLE", "CANCELLED", "INTERNAL"})
    void statusDeFalhaDaRpcImpedeTodaGravacaoNova(String codigoGrpc) {
        long outra = novaCamara("LOTE-02");
        var anterior = leitura(camara, "a", -10, 4);
        enviar(lote(anterior)).statusCode(200);
        go.comportarSe(ServidorTelemetriaDeTeste.falhando(Status.fromCode(Status.Code.valueOf(codigoGrpc))));

        enviar(lote(anterior, leitura(camara, "b", -9, 4), leitura(outra, "b", -9, 4))).statusCode(503)
                .body("type", equalTo("/problemas/analise-indisponivel"));
        assertEquals(2, go.chamadas.get());
        assertEquals(1, total(camara));
        assertEquals(0, total(outra));
    }

    @Test
    void prazoCancelaAUnicaRpcEImpedeGravacoes() throws InterruptedException {
        go.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(10)));
        enviar(lote(leitura(camara, "a", -10, 4))).statusCode(503);
        assertTrue(go.observouCancelamento(Duration.ofSeconds(3)));
        assertEquals(1, go.chamadas.get());
        assertEquals(0, total(camara));
    }

    @Test
    void respostaGlobalForaDoContratoRejeitaTodoOLoteAntesDeGravar() {
        long outra = novaCamara("LOTE-02");
        go.comportarSe(ServidorTelemetriaDeTeste.analisando(resposta -> resposta.toBuilder().removeResults(1).build()));
        enviar(lote(leitura(camara, "a", -10, 4), leitura(outra, "a", -10, 4))).statusCode(502)
                .body("type", equalTo("/problemas/falha-de-integracao"));
        assertEquals(1, go.chamadas.get());
        assertEquals(0, total(camara));
        assertEquals(0, total(outra));
    }

    @Test
    void validaSintaxeEstruturaLimiteETiposAntesDeConsultarAnalise() {
        var valida = leitura(camara, "a", -10, 9);
        enviar("[{}").statusCode(400).body("type", equalTo("/problemas/requisicao-invalida"));
        enviar(lote(valida) + "{}").statusCode(400);
        enviar("{}").statusCode(422);
        enviar("null").statusCode(422);
        enviar("[]").statusCode(422);
        enviar(lote(valida, "null")).statusCode(422);
        enviar(lote(valida, "{}")).statusCode(422);
        enviar(lote(valida, valida.replace("\"temperatura\":9", "\"temperatura\":\"9\""))).statusCode(422);
        enviar(lote(valida.replace("\"sensor\"", "\"\\u2003\""))).statusCode(422)
                .body("erros[0].campo", equalTo("leituras[0].origem"));
        enviar(lote(valida, leitura(camara, "s", 6, 4))).statusCode(422)
                .body("erros[0].campo", equalTo("leituras[1].medidaEm"));
        enviar(lote(valida, valida)).statusCode(422).body("erros[0].campo", equalTo("leituras[1].idExterno"));
        enviar(lote(IntStream.range(0, 51).mapToObj(i -> leitura(camara, "s" + i, i - 60, 4))
                .toArray(String[]::new))).statusCode(422);
        assertEquals(0, go.chamadas.get());
        assertEquals(0, total(camara));
    }

    @Test
    void aceitaCinquentaCandidatasNaMesmaCamara() {
        enviar(lote(IntStream.range(0, 50).mapToObj(i -> leitura(camara, "s" + i, i - 60, 4))
                .toArray(String[]::new))).statusCode(200).body("size()", equalTo(50), "[49].codigo", equalTo(201));
        assertEquals(1, go.chamadas.get());
        assertEquals(50, go.ultimaRequisicao().getWindows(0).getCandidatesCount());
        assertEquals(50, total(camara));
    }

    @Test
    void protegeRotaEPublicaContratoNoOpenApi() {
        var entradas = lote(leitura(camara, "a", -10, 9));
        RestAssured.given().contentType(ContentType.JSON).body(entradas).post(ROTA).then().statusCode(401);
        enviar(Perfil.AUDITOR, entradas).statusCode(403);
        assertEquals(0, go.chamadas.get());
        assertEquals(0, total(camara));

        var contrato = "paths.'/api/leituras/lote'.post";
        var requisicao = contrato + ".requestBody.content.'application/json'.schema";
        var resposta = contrato + ".responses.'200'.content.'application/json'.schema";
        RestAssured.given().queryParam("format", "json").get("/q/openapi").then().statusCode(200)
                .body(contrato + ".security[0].keySet()", hasItems("bearerAuth"),
                        requisicao + ".type", equalTo("array"),
                        requisicao + ".minItems", equalTo(1), requisicao + ".maxItems", equalTo(50),
                        requisicao + ".items.'$ref'", endsWith("/LeituraRequest"),
                        resposta + ".type", equalTo("array"),
                        resposta + ".items.'$ref'", endsWith("/LeituraLoteResponse"),
                        contrato + ".responses.keySet()", hasItems("400", "401", "403", "422", "503"));
    }

    private long novaCamara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara de teste", "UBS", 100,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA)).getId();
    }

    private long total(long camaraId) {
        return leituras.listarDaCamara(camaraId, PAGINA).totalElementos();
    }

    private static String leitura(long camaraId, String idExterno, int minutos, int temperatura) {
        return """
                {"camaraId":%d,"idExterno":"%s","temperatura":%d,"medidaEm":"%s","origem":"sensor"}
                """.formatted(camaraId, idExterno, temperatura, AGORA.plusMinutes(minutos));
    }

    private static String lote(String... leituras) {
        return Arrays.stream(leituras).collect(Collectors.joining(",", "[", "]"));
    }

    private static ValidatableResponse enviar(String corpo) {
        return enviar(Perfil.OPERADOR, corpo);
    }

    private static ValidatableResponse enviar(Perfil perfil, String corpo) {
        return autenticado(perfil).contentType(ContentType.JSON).header("X-Correlation-Id", CORRELACAO)
                .body(corpo).post(ROTA).then();
    }
}
