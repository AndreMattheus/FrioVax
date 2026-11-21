package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamara;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamaraDTO;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocio;
import br.ufrn.friovax.api.lote.aplicacao.CadastrarLote;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaDeTeste;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaResource;
import br.ufrn.friovax.api.telemetria.integracao.TelemetriaGrpcPostgresProfile;
import io.grpc.Status;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.TransactionalException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Recepção em transações reais e simultâneas no PostgreSQL, com o servidor gRPC de teste no lugar do Go. Faixa
 * 2–8 °C e N=3. Prova que o lock da câmara não fica retido durante a RPC e que a chave única e a revalidação
 * impedem duplicação e leituras fora de ordem.
 */
@QuarkusTest
@TestProfile(TelemetriaGrpcPostgresProfile.class)
class RecepcaoDeLeituraPostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    @Inject ReceberLeitura receber;
    @Inject CadastrarLote cadastrarLote;
    @Inject AtualizarCamara atualizarCamara;
    @Inject CamaraRepository camaras;
    @Inject EntityManager entityManager;

    private ServidorTelemetriaDeTeste go;
    private final List<Long> camarasDoTeste = new ArrayList<>();
    private long camaraId;
    private OffsetDateTime inicio;

    @BeforeEach
    void preparar() {
        go = ServidorTelemetriaResource.servidor;
        go.reiniciarRegistros();
        camaraId = novaCamara();
        inicio = OffsetDateTime.now(FUSO).minusHours(1).withNano(0);
    }

    @AfterEach
    void limpar() {
        go.reiniciarRegistros();
        // Leituras e versões de política saem em cascata com a câmara; lotes não têm cascata.
        QuarkusTransaction.requiringNew().run(() -> camarasDoTeste.forEach(id -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        }));
    }

    @Test
    void mesmaChaveEnviadaAoMesmoTempoGravaUmaLeituraEDevolveOMesmoRegistro() throws Exception {
        var tarefas = new ArrayList<Callable<LeituraRecebida>>();
        for (int i = 0; i < 6; i++) {
            tarefas.add(() -> receber("S-1", 0, "9.0"));
        }

        var resultados = simultaneamente(tarefas);

        var recebidas = resultados.stream().map(r -> (LeituraRecebida) r).toList();
        assertEquals(1, recebidas.stream().filter(LeituraRecebida::criada).count());
        assertEquals(1, recebidas.stream().map(r -> r.leitura().getId()).distinct().count());
        assertEquals(1, leiturasGravadas());
    }

    @Test
    void chavesDistintasAoMesmoTempoNaMesmaCamaraSoConfirmamEmOrdemENaSequenciaCerta() throws Exception {
        var tarefas = new ArrayList<Callable<LeituraRecebida>>();
        for (int i = 0; i < 6; i++) {
            int minuto = i;
            tarefas.add(() -> receber("S-" + minuto, minuto, "9.0"));
        }

        var resultados = simultaneamente(tarefas);

        long aceitas = resultados.stream().filter(LeituraRecebida.class::isInstance).count();
        assertTrue(aceitas >= 1, "nenhuma leitura aceita: " + resultados);
        assertTrue(resultados.stream().filter(r -> !(r instanceof LeituraRecebida))
                .allMatch(ConflitoDeNegocio.class::isInstance), () -> "falhas inesperadas: " + resultados);
        var gravadas = gravadasEmOrdemDeAceitacao();
        assertEquals(aceitas, gravadas.size());
        for (int i = 0; i < gravadas.size(); i++) {
            if (i > 0) {
                assertTrue(gravadas.get(i).instante().isAfter(gravadas.get(i - 1).instante()),
                        "leitura aceita fora de ordem: " + gravadas);
            }
            // Cada aceita foi analisada com o contexto confirmado imediatamente antes dela.
            assertEquals(Math.min(i + 1, 3), gravadas.get(i).sequencia(), "sequência incoerente: " + gravadas);
        }
    }

    @Test
    void alocacaoDeLoteNaMesmaCamaraNaoEsperaOPrazoDoGo() throws Exception {
        go.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(10)));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var leitura = executor.submit(() -> receber("S-1", 0, "9.0"));
            assertTrue(go.iniciada.await(10, TimeUnit.SECONDS), "a análise não começou");

            var inicioDaAlocacao = System.nanoTime();
            cadastrarLote.cadastrar(codigoDeLote(), "Febre amarela", "Bio-Manguinhos",
                    LocalDate.now(FUSO).plusYears(1), 100, camaraId);
            var alocacao = Duration.ofNanos(System.nanoTime() - inicioDaAlocacao);

            assertFalse(leitura.isDone(), "a leitura terminou antes da alocação");
            assertTrue(alocacao.compareTo(TelemetriaGrpcPostgresProfile.PRAZO) < 0,
                    "a alocação esperou o Go: " + alocacao);
            var falha = assertInstanceOf(AnaliseIndisponivel.class, falhaDe(leitura));
            assertEquals(AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO, falha.motivo());
        }
        assertEquals(0, leiturasGravadas());
    }

    @Test
    void politicaAlteradaDuranteARpcRefazAAnaliseUmaVezNaNovaVersao() {
        receber("S-1", 0, "9.0");
        var primeira = new AtomicBoolean(true);
        go.comportarSe((requisicao, resposta) -> {
            if (primeira.getAndSet(false)) {
                // Roda noutra thread e noutra transação: se a recepção retivesse o lock, ficaria esperando.
                atualizarCamara.atualizar(camaraId, new AtualizarCamaraDTO("Câmara", "UBS recepção", 1000,
                        new BigDecimal("0.0"), new BigDecimal("10.0"), EstadoCamara.OPERACIONAL));
            }
            resposta.onNext(ServidorTelemetriaDeTeste.analisar(requisicao));
            resposta.onCompleted();
        });

        var recebida = receber("S-2", 1, "9.0");

        assertTrue(recebida.criada());
        assertEquals(2, recebida.leitura().getPoliticaVersao());
        assertEquals(ResultadoTermico.segura(), recebida.leitura().getResultado());
        assertEquals(3, go.chamadas.get(), "S-1 e as duas análises de S-2");
        assertEquals(0, go.ultimaRequisicao().getWindows(0).getContextCount());
    }

    @Test
    void goForaDoArAntesDaRpcNaoGravaNada() {
        go.comportarSe(ServidorTelemetriaDeTeste.falhando(Status.UNAVAILABLE));

        var falha = assertThrows(AnaliseIndisponivel.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(AnaliseIndisponivel.Motivo.SERVICO_INACESSIVEL, falha.motivo());
        assertEquals(0, leiturasGravadas());
    }

    @Test
    void reenvioDepoisDoCommitRecuperaOResultadoPersistidoSemNovaAnalise() {
        var corpo = Map.of("camaraId", camaraId, "idExterno", "S-1", "temperatura", new BigDecimal("9.0"),
                "medidaEm", inicio.toString(), "origem", "sensor-1");
        var primeira = autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).body(corpo)
                .post("/api/leituras").then().statusCode(201).extract().response();

        var reenvio = autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).body(corpo)
                .post("/api/leituras").then().statusCode(200).extract().response();

        var location = primeira.header("Location");
        assertTrue(location.endsWith("/api/leituras/" + primeira.jsonPath().getLong("id")));
        var consulta = autenticado(Perfil.AUDITOR).get(location).then().statusCode(200).extract().response();
        assertEquals(primeira.jsonPath().getMap(""), reenvio.jsonPath().getMap(""));
        assertEquals(primeira.jsonPath().getMap(""), consulta.jsonPath().getMap(""));
        assertEquals(1, go.chamadas.get());
        assertEquals(1, leiturasGravadas());
    }

    @Test
    void recepcaoDentroDeUmaTransacaoAbertaEhRecusada() {
        assertThrows(TransactionalException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> receber("S-1", 0, "9.0")));

        assertEquals(0, go.chamadas.get());
        assertEquals(0, leiturasGravadas());
    }

    private LeituraRecebida receber(String idExterno, int minutos, String temperatura) {
        return receber.receber(camaraId, idExterno, new BigDecimal(temperatura), inicio.plusMinutes(minutos),
                "sensor-1");
    }

    private record Gravada(OffsetDateTime instante, int sequencia) {
    }

    private List<Gravada> gravadasEmOrdemDeAceitacao() {
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> linhas = entityManager.createNativeQuery(
                            "select instante, sequencia from leituras where camara_id = :id order by id")
                    .setParameter("id", camaraId).getResultList();
            return linhas.stream()
                    .map(l -> new Gravada(instante(l[0]), ((Number) l[1]).intValue()))
                    .toList();
        });
    }

    private static OffsetDateTime instante(Object valor) {
        return switch (valor) {
            case OffsetDateTime instante -> instante;
            case java.time.Instant instante -> instante.atOffset(java.time.ZoneOffset.UTC);
            case java.sql.Timestamp timestamp -> timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
            default -> throw new IllegalStateException("tipo inesperado: " + valor.getClass());
        };
    }

    private long leiturasGravadas() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from leituras where camara_id = :id")
                .setParameter("id", camaraId).getSingleResult()).longValue());
    }

    /** Executa ao mesmo tempo e devolve, na ordem, o retorno de cada tarefa ou a exceção que ela lançou. */
    private static List<Object> simultaneamente(List<Callable<LeituraRecebida>> tarefas) throws Exception {
        var prontas = new CountDownLatch(tarefas.size());
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tarefas.size())) {
            var futuros = new ArrayList<Future<LeituraRecebida>>();
            for (var tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    prontas.countDown();
                    if (!largada.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("largada não recebida");
                    }
                    return tarefa.call();
                }));
            }
            assertTrue(prontas.await(10, TimeUnit.SECONDS));
            largada.countDown();
            var resultados = new ArrayList<Object>();
            for (var futuro : futuros) {
                var falha = falhaDe(futuro);
                resultados.add(falha != null ? falha : futuro.get());
            }
            return resultados;
        }
    }

    private static Throwable falhaDe(Future<?> futuro) throws InterruptedException {
        try {
            futuro.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            return fail("a operação não terminou a tempo", e);
        }
    }

    private long novaCamara() {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Câmara",
                "UBS recepção", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now(FUSO))).getId());
        camarasDoTeste.add(id);
        return id;
    }

    private static String codigoDeLote() {
        return "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }
}
