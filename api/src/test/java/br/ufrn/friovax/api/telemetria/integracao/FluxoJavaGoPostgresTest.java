package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo de ponta a ponta da S2-20: leitura → RPC gRPC real para o binário Go em container → leitura, ocorrência,
 * estado térmico e auditoria no PostgreSQL real → consulta HTTP. As credenciais são as contas de teste provisionadas
 * pela própria API; os tokens vêm de {@code /api/auth/login}. Política, cache e auditoria têm testes próprios: aqui só
 * se confere o que atravessa as três peças.
 *
 * <p>Os prazos das falhas controladas são medidos e gravados em {@code target/fluxo-real/relatorio.json}.
 */
@QuarkusTest
@TestProfile(FluxoRealProfile.class)
class FluxoJavaGoPostgresTest {

    /** Limite da issue para a resposta 503 com o Go fora do ar. */
    static final Duration LIMITE_FALHA = Duration.ofSeconds(2);
    private static final String PROBLEMA_INDISPONIVEL = "/problemas/analise-indisponivel";
    private static final Path RELATORIO = Path.of("target", "fluxo-real", "relatorio.json");
    private static final List<Map<String, Object>> MEDICOES = new CopyOnWriteArrayList<>();

    private static Sessao administrador;
    private static Sessao operador;
    private static Sessao auditor;

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject LeituraRepository leituras;
    @Inject OcorrenciaRepository ocorrencias;
    @Inject EntityManager entityManager;

    private final List<Long> camarasCriadas = new ArrayList<>();
    private final List<String> correlacoes = new ArrayList<>();
    private OffsetDateTime inicio;

    @BeforeEach
    void preparar() {
        inicio = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0);
        if (administrador == null) {
            administrador = entrar("administrador");
            operador = entrar("operador");
            auditor = entrar("auditor");
        }
    }

    @AfterEach
    void limpar() {
        if (camarasCriadas.isEmpty()) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> {
            if (!correlacoes.isEmpty()) {
                entityManager.createNativeQuery("delete from eventos_auditoria where correlacao_id in (:ids)")
                        .setParameter("ids", correlacoes).executeUpdate();
            }
            entityManager.createNativeQuery("delete from ocorrencias where camara_id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
            entityManager.createNativeQuery("delete from lotes where camara_id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
        });
    }

    @AfterAll
    static void gravarRelatorio() throws IOException {
        var relatorio = new LinkedHashMap<String, Object>();
        relatorio.put("suite", FluxoJavaGoPostgresTest.class.getName());
        relatorio.put("geradoEm", Instant.now().toString());
        relatorio.put("imagemGo", ServicoGoResource.imagem());
        relatorio.put("limiteFalhaMs", LIMITE_FALHA.toMillis());
        relatorio.put("medicoes", List.copyOf(MEDICOES));
        Files.createDirectories(RELATORIO.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(RELATORIO.toFile(), relatorio);
        System.out.println("Relatório do fluxo Java–Go–banco: " + RELATORIO.toAbsolutePath());
        MEDICOES.forEach(medicao -> System.out.println("  " + medicao));
    }

    @Test
    void tresInsegurasAbremUmaOcorrenciaComSnapshotEReenvioDaTerceiraNaoDuplica() {
        long camaraId = novaCamara();
        var lote = novoLote(camaraId);
        int analisesAntes = analisesConcluidas();

        long a = receber(camaraId, "a", 0, 9).statusCode(201)
                .body("classificacao", equalTo("INSEGURA"), "sequencia", equalTo(1),
                        "ocorrenciaAbertaId", nullValue())
                .extract().jsonPath().getLong("id");
        long b = receber(camaraId, "b", 1, 9).statusCode(201)
                .body("sequencia", equalTo(2), "ocorrenciaAbertaId", nullValue())
                .extract().jsonPath().getLong("id");
        var correlacaoDaTerceira = novaCorrelacao();
        var terceira = receber(camaraId, "c", 2, 9, correlacaoDaTerceira).statusCode(201)
                .body("sequencia", equalTo(3), "limiarAtingido", equalTo(true),
                        "ocorrenciaAbertaId", notNullValue())
                .extract().jsonPath();
        long c = terceira.getLong("id");
        long ocorrenciaId = terceira.getLong("ocorrenciaAbertaId");
        assertEquals(3, analisesConcluidas() - analisesAntes, "cada leitura nova passa por uma RPC ao Go");

        receber(camaraId, "c", 2, 9).statusCode(200)
                .body("id", equalTo((int) c), "ocorrenciaAbertaId", equalTo((int) ocorrenciaId),
                        "sequencia", equalTo(3));
        assertEquals(3, analisesConcluidas() - analisesAntes, "o reenvio devolve o registro sem nova análise");

        assertEquals(3, totalDeLeituras(camaraId));
        assertEquals(1, totalDeOcorrencias(camaraId));
        como(auditor).get("/api/ocorrencias/{id}", ocorrenciaId).then().statusCode(200)
                .body("estado", equalTo("ABERTA"), "camaraId", equalTo((int) camaraId),
                        "leituraAberturaId", equalTo((int) c),
                        "evidencias.id", contains((int) a, (int) b, (int) c),
                        "politica.versao", equalTo(1), "politica.limiar", equalTo(3),
                        "lotesAfetados.loteId", contains(lote.getId().intValue()),
                        "lotesAfetados.codigo", contains(lote.getCodigo()),
                        "lotesAfetados.quantidade", contains(25));
        assertEquals("EM_ALERTA", estadoTermico(camaraId));

        var aberturas = eventos(ocorrenciaId, "ABERTURA_DE_OCORRENCIA");
        assertEquals(1, aberturas.size(), "uma só abertura auditada, mesmo com o reenvio");
        assertEquals(operador.id().toString(), aberturas.getFirst()[0], "autor: o operador autenticado");
        assertEquals(correlacaoDaTerceira, aberturas.getFirst()[1], "correlação da requisição que abriu");
    }

    @Test
    void loteComDuasCamarasAnalisaCadaUmaIsoladamenteEReenvioNaoDuplicaNada() {
        long comAlerta = novaCamara();
        long semAlerta = novaCamara();
        novoLote(comAlerta);
        var entradas = List.of(leitura(comAlerta, "a1", 0, 9), leitura(semAlerta, "b1", 0, 9),
                leitura(comAlerta, "a2", 1, 9), leitura(semAlerta, "b2", 1, 9),
                leitura(comAlerta, "a3", 2, 9), leitura(comAlerta, "a4", 3, 4));
        int analisesAntes = analisesConcluidas();
        var correlacaoDoLote = novaCorrelacao();

        var primeiro = receberLote(entradas, correlacaoDoLote).statusCode(200)
                .body("codigo", contains(201, 201, 201, 201, 201, 201),
                        "sequencia", contains(1, 1, 2, 2, 3, 0),
                        "classificacao", contains("INSEGURA", "INSEGURA", "INSEGURA", "INSEGURA", "INSEGURA",
                                "SEGURA"),
                        "limiarAtingido", contains(false, false, false, false, true, false),
                        "[1].ocorrenciaId", nullValue(), "[3].ocorrenciaId", nullValue(),
                        "[4].ocorrenciaId", notNullValue(), "[5].ocorrenciaId", nullValue())
                .extract().jsonPath();
        long ocorrenciaId = primeiro.getLong("[4].ocorrenciaId");
        List<Integer> ids = primeiro.getList("id", Integer.class);
        assertEquals(1, analisesConcluidas() - analisesAntes, "o lote vai ao Go numa única RPC com duas janelas");

        var aberta = ocorrencias.abertaDaCamara(comAlerta).orElseThrow();
        assertEquals(ocorrenciaId, aberta.getId());
        assertEquals(List.of(ids.get(0).longValue(), ids.get(2).longValue(), ids.get(4).longValue()),
                aberta.getEvidencias());
        assertEquals("EM_ALERTA", estadoTermico(comAlerta), "a segura depois do limiar não fecha o alerta");
        assertTrue(ocorrencias.abertaDaCamara(semAlerta).isEmpty());
        assertEquals("ATENCAO", estadoTermico(semAlerta));
        var antes = contagens(comAlerta, semAlerta, correlacaoDoLote);
        assertEquals(List.of(4L, 2L, 1L, 0L), antes.subList(0, 4), "leituras e ocorrências de cada câmara");
        assertTrue(antes.get(4) >= 1, "a abertura foi auditada com a correlação do lote");

        var correlacaoDoReenvio = novaCorrelacao();
        receberLote(entradas, correlacaoDoReenvio).statusCode(200)
                .body("codigo", everyItem(equalTo(200)), "id", equalTo(ids),
                        "[4].ocorrenciaId", equalTo((int) ocorrenciaId));
        assertEquals(1, analisesConcluidas() - analisesAntes, "reenvio integral não chama o Go");
        assertEquals(antes, contagens(comAlerta, semAlerta, correlacaoDoLote));
        assertEquals(0, eventosDaCorrelacao(correlacaoDoReenvio), "reenvio não gera auditoria");
    }

    @Test
    void goParadoDevolve503RapidoSemGravarNadaEConsultasContinuam() {
        long camaraId = novaCamara();
        novoLote(camaraId);
        receber(camaraId, "a", 0, 9).statusCode(201);
        receber(camaraId, "b", 1, 9).statusCode(201);

        ServicoGoResource.parar();
        try {
            medirIndisponibilidade("go-parado-leitura",
                    () -> receber(camaraId, "c", 2, 9));
            medirIndisponibilidade("go-parado-lote",
                    () -> receberLote(List.of(leitura(camaraId, "c", 2, 9)), novaCorrelacao()));
            conferirNadaGravado(camaraId, "c");
            conferirConsultasSemGo(camaraId);
        } finally {
            ServicoGoResource.iniciar();
        }

        reenviarAteOGoVoltar(camaraId, "c", 2, 9).statusCode(201)
                .body("sequencia", equalTo(3), "ocorrenciaAbertaId", notNullValue());
        assertEquals(1, totalDeOcorrencias(camaraId));
    }

    @Test
    void goSemResponderEsgotaOPrazoSemGravarNadaEConsultasContinuam() {
        long camaraId = novaCamara();
        novoLote(camaraId);
        receber(camaraId, "a", 0, 9).statusCode(201);
        receber(camaraId, "b", 1, 9).statusCode(201);

        ServicoGoResource.congelar();
        try {
            medirIndisponibilidade("go-congelado-leitura",
                    () -> receber(camaraId, "c", 2, 9));
            medirIndisponibilidade("go-congelado-lote",
                    () -> receberLote(List.of(leitura(camaraId, "c", 2, 9)), novaCorrelacao()));
            conferirNadaGravado(camaraId, "c");
            conferirConsultasSemGo(camaraId);
        } finally {
            ServicoGoResource.descongelar();
        }

        reenviarAteOGoVoltar(camaraId, "c", 2, 9).statusCode(201)
                .body("sequencia", equalTo(3), "ocorrenciaAbertaId", notNullValue());
        assertEquals(1, totalDeOcorrencias(camaraId));
    }

    @Test
    void seguraSeguidaDeResolucaoEncerraComAutorRealENovaSequenciaAbreOutraOcorrencia() {
        long camaraId = novaCamara();
        novoLote(camaraId);
        receber(camaraId, "a", 0, 9).statusCode(201);
        receber(camaraId, "b", 1, 9).statusCode(201);
        long primeira = receber(camaraId, "c", 2, 9).statusCode(201)
                .extract().jsonPath().getLong("ocorrenciaAbertaId");

        resolver(primeira, "Porta reparada").statusCode(409)
                .body("type", equalTo("/problemas/resolucao-nao-permitida"));

        long segura = receber(camaraId, "d", 3, 4).statusCode(201)
                .body("classificacao", equalTo("SEGURA"), "sequencia", equalTo(0),
                        "ocorrenciaAbertaId", nullValue())
                .extract().jsonPath().getLong("id");
        assertEquals("EM_ALERTA", estadoTermico(camaraId));

        var correlacaoDaResolucao = novaCorrelacao();
        resolver(primeira, "Porta reparada", correlacaoDaResolucao).statusCode(200)
                .body("estado", equalTo("RESOLVIDA"), "resolvidaPor", equalTo(administrador.id().toString()),
                        "justificativa", equalTo("Porta reparada"), "leituraResolucaoId", equalTo((int) segura),
                        "resolvidaEm", notNullValue());
        assertEquals("NORMAL", estadoTermico(camaraId));
        var resolucoes = eventos(primeira, "RESOLUCAO_DE_OCORRENCIA");
        assertEquals(1, resolucoes.size());
        assertEquals(administrador.id().toString(), resolucoes.getFirst()[0]);
        assertEquals(correlacaoDaResolucao, resolucoes.getFirst()[1]);

        receber(camaraId, "e", 4, 9).statusCode(201).body("sequencia", equalTo(1));
        receber(camaraId, "f", 5, 9).statusCode(201).body("sequencia", equalTo(2));
        var nova = receber(camaraId, "g", 6, 9).statusCode(201)
                .body("sequencia", equalTo(3), "ocorrenciaAbertaId", notNullValue())
                .extract().jsonPath();
        long segunda = nova.getLong("ocorrenciaAbertaId");

        assertNotEquals(primeira, segunda, "o novo episódio tem nova identidade");
        assertEquals(2, totalDeOcorrencias(camaraId));
        assertEquals("EM_ALERTA", estadoTermico(camaraId));
        como(auditor).get("/api/ocorrencias/{id}", primeira).then().statusCode(200)
                .body("estado", equalTo("RESOLVIDA"), "leituraResolucaoId", equalTo((int) segura));
        como(auditor).get("/api/ocorrencias/{id}", segunda).then().statusCode(200)
                .body("estado", equalTo("ABERTA"), "leituraAberturaId", equalTo(nova.getInt("id")),
                        "resolvidaPor", nullValue(), "evidencias.idExterno", contains("e", "f", "g"));
    }

    // --- falhas controladas -------------------------------------------------------------------------------------

    private void medirIndisponibilidade(String cenario, Supplier<ValidatableResponse> chamada) {
        long inicioDaChamada = System.nanoTime();
        var resposta = chamada.get();
        var decorrido = Duration.ofNanos(System.nanoTime() - inicioDaChamada);
        int status = resposta.extract().statusCode();
        var medicao = new LinkedHashMap<String, Object>();
        medicao.put("cenario", cenario);
        medicao.put("status", status);
        medicao.put("duracaoMs", decorrido.toMillis());
        medicao.put("dentroDoLimite", decorrido.compareTo(LIMITE_FALHA) <= 0);
        MEDICOES.add(medicao);
        resposta.statusCode(503).contentType("application/problem+json")
                .body("type", equalTo(PROBLEMA_INDISPONIVEL));
        assertTrue(decorrido.compareTo(LIMITE_FALHA) <= 0,
                () -> cenario + ": 503 em " + decorrido.toMillis() + " ms, acima de " + LIMITE_FALHA.toMillis());
    }

    private void conferirNadaGravado(long camaraId, String idExterno) {
        assertTrue(leituras.buscarPorChave(camaraId, idExterno).isEmpty(), "leitura parcial gravada");
        assertEquals(2, totalDeLeituras(camaraId));
        assertEquals(0, totalDeOcorrencias(camaraId), "ocorrência parcial gravada");
        assertEquals("ATENCAO", estadoTermico(camaraId));
    }

    /** Inventário e histórico só dependem do banco. */
    private void conferirConsultasSemGo(long camaraId) {
        como(auditor).get("/api/camaras").then().statusCode(200);
        como(auditor).get("/api/camaras/{id}", camaraId).then().statusCode(200)
                .body("estadoTermico", equalTo("ATENCAO"));
        como(auditor).get("/api/lotes").then().statusCode(200);
        como(auditor).get("/api/camaras/{id}/leituras", camaraId).then().statusCode(200)
                .body("totalElements", equalTo(2));
        como(auditor).queryParam("camaraId", camaraId).get("/api/ocorrencias").then().statusCode(200)
                .body("totalElements", equalTo(0));
    }

    /** O canal gRPC reconecta com backoff; reenviar a mesma chave é seguro porque o 503 não grava nada. */
    private ValidatableResponse reenviarAteOGoVoltar(long camaraId, String idExterno, int minuto, int temperatura) {
        long limite = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (true) {
            var resposta = receber(camaraId, idExterno, minuto, temperatura);
            if (resposta.extract().statusCode() != 503 || System.nanoTime() > limite) {
                return resposta;
            }
            ServicoGoResource.dormir(250);
        }
    }

    // --- requisições --------------------------------------------------------------------------------------------

    private ValidatableResponse receber(long camaraId, String idExterno, int minuto, int temperatura) {
        return receber(camaraId, idExterno, minuto, temperatura, novaCorrelacao());
    }

    private ValidatableResponse receber(long camaraId, String idExterno, int minuto, int temperatura,
                                        String correlacao) {
        return como(operador).header("X-Correlation-Id", correlacao).contentType(ContentType.JSON)
                .body(leitura(camaraId, idExterno, minuto, temperatura)).post("/api/leituras").then();
    }

    private ValidatableResponse receberLote(List<Map<String, Object>> entradas, String correlacao) {
        return como(operador).header("X-Correlation-Id", correlacao).contentType(ContentType.JSON)
                .body(entradas).post("/api/leituras/lote").then();
    }

    private ValidatableResponse resolver(long ocorrenciaId, String justificativa) {
        return resolver(ocorrenciaId, justificativa, novaCorrelacao());
    }

    private ValidatableResponse resolver(long ocorrenciaId, String justificativa, String correlacao) {
        return como(administrador).header("X-Correlation-Id", correlacao).contentType(ContentType.JSON)
                .body(Map.of("justificativa", justificativa))
                .post("/api/ocorrencias/{id}/resolucao", ocorrenciaId).then();
    }

    private Map<String, Object> leitura(long camaraId, String idExterno, int minuto, int temperatura) {
        return Map.of("camaraId", camaraId, "idExterno", idExterno, "temperatura", temperatura,
                "medidaEm", inicio.plusMinutes(minuto).toString(), "origem", "sensor-fluxo");
    }

    private String novaCorrelacao() {
        var correlacao = "fluxo-" + UUID.randomUUID();
        correlacoes.add(correlacao);
        return correlacao;
    }

    private static RequestSpecification como(Sessao sessao) {
        return RestAssured.given().auth().oauth2(sessao.token());
    }

    private static Sessao entrar(String login) {
        Response resposta = RestAssured.given().contentType(ContentType.JSON)
                .body(Map.of("login", login, "senha", FluxoRealProfile.SENHA)).post("/api/auth/login");
        resposta.then().statusCode(200).body("identidade.login", equalTo(login));
        return new Sessao(resposta.path("accessToken"), UUID.fromString(resposta.path("identidade.id")));
    }

    private record Sessao(String token, UUID id) {
    }

    // --- dados ---------------------------------------------------------------------------------------------------

    private long novaCamara() {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(codigo(), "Câmara do fluxo",
                "UBS fluxo real", 100, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                inicio.minusDays(1))).getId());
        camarasCriadas.add(id);
        return id;
    }

    private Lote novoLote(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.salvar(Lote.novo(codigo(), "Vacina do fluxo",
                "Laboratório", inicio.toLocalDate().plusDays(30), 25, camaraId, inicio.toLocalDate(), inicio)));
    }

    private static String codigo() {
        return "FX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Análises concluídas com sucesso segundo o log do próprio processo Go. */
    private static int analisesConcluidas() {
        // O log do container chega de forma assíncrona; espera estabilizar antes de contar.
        long anterior = -1;
        for (int tentativa = 0; tentativa < 20; tentativa++) {
            long atual = ServicoGoResource.analisesNoLog().stream().filter(a -> a.codigo().equals("OK")).count();
            if (atual == anterior) {
                return (int) atual;
            }
            anterior = atual;
            ServicoGoResource.dormir(100);
        }
        return (int) anterior;
    }

    private long totalDeLeituras(long camaraId) {
        return leituras.listarDaCamara(camaraId, Paginacao.padrao()).totalElementos();
    }

    private long totalDeOcorrencias(long camaraId) {
        return contar("select count(*) from ocorrencias where camara_id = :id", camaraId);
    }

    private String estadoTermico(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> (String) entityManager
                .createNativeQuery("select estado_termico from camaras where id = :id")
                .setParameter("id", camaraId).getSingleResult());
    }

    /** Leituras e ocorrências das duas câmaras e eventos de auditoria gravados com a correlação do lote. */
    private List<Long> contagens(long comAlerta, long semAlerta, String correlacao) {
        return List.of(totalDeLeituras(comAlerta), totalDeLeituras(semAlerta), totalDeOcorrencias(comAlerta),
                totalDeOcorrencias(semAlerta), eventosDaCorrelacao(correlacao));
    }

    private long eventosDaCorrelacao(String correlacao) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
                        "select count(*) from eventos_auditoria where correlacao_id = :correlacao")
                .setParameter("correlacao", correlacao).getSingleResult()).longValue());
    }

    /** ator_id (texto) e correlacao_id dos eventos da operação sobre a ocorrência. */
    @SuppressWarnings("unchecked")
    private List<Object[]> eventos(long ocorrenciaId, String operacao) {
        return QuarkusTransaction.requiringNew().call(() -> (List<Object[]>) entityManager.createNativeQuery(
                        "select cast(ator_id as text), correlacao_id from eventos_auditoria "
                                + "where recurso = 'OCORRENCIA' and recurso_id = :id and operacao = :operacao "
                                + "order by id")
                .setParameter("id", ocorrenciaId).setParameter("operacao", operacao).getResultList());
    }

    private long contar(String sql, long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(sql)
                .setParameter("id", camaraId).getSingleResult()).longValue());
    }
}
