package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.suporte.PortaSemServico;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela.MotivoDaRejeicao;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import br.ufrn.friovax.telemetry.v1.AnalyzeReadingsResponse;
import br.ufrn.friovax.telemetry.v1.ReadingClassification;
import br.ufrn.friovax.telemetry.v1.TelemetryServiceGrpc;
import br.ufrn.friovax.telemetry.v1.WindowAnalysis;
import br.ufrn.friovax.telemetry.v1.WindowError;
import br.ufrn.friovax.telemetry.v1.WindowErrorCode;
import br.ufrn.friovax.telemetry.v1.WindowResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static br.ufrn.friovax.api.telemetria.integracao.JanelasDeTeste.POLITICA;
import static br.ufrn.friovax.api.telemetria.integracao.JanelasDeTeste.janela;
import static br.ufrn.friovax.api.telemetria.integracao.JanelasDeTeste.leitura;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adaptador contra um servidor gRPC real de teste: serialização, transporte, prazo e tradução de falhas. */
class AnaliseTermicaGrpcTest {

    private static final Duration PRAZO = Duration.ofMillis(800);

    private ServidorTelemetriaDeTeste servidor;
    private ManagedChannel canal;

    @BeforeEach
    void iniciar() {
        servidor = new ServidorTelemetriaDeTeste();
        canal = canalPara(servidor.porta());
    }

    @AfterEach
    void encerrar() throws InterruptedException {
        canal.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        servidor.close();
    }

    @Test
    void enviaDecimosTimestampUtcECorrelacaoNoCorpoENoMetadado() {
        var instante = OffsetDateTime.parse("2026-11-10T09:15:30.123456-03:00");
        var contexto = List.of(
                new LeituraDaAnalise("ctx-1", instante.minusMinutes(2), new BigDecimal("-1.5")),
                new LeituraDaAnalise("ctx-2", instante.minusMinutes(1), new BigDecimal("0")));
        var candidatas = List.of(
                new LeituraDaAnalise("nova-1", instante, new BigDecimal("8.7")),
                new LeituraDaAnalise("nova-2", instante.plusSeconds(1), new BigDecimal("999.90")));
        var janela = new JanelaDeAnalise(7, POLITICA, contexto, candidatas);

        adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr-123", janela));

        var requisicao = servidor.ultimaRequisicao();
        assertEquals("corr-123", requisicao.getCorrelationId());
        assertEquals(List.of("corr-123"), servidor.correlacoesNoMetadado);
        var enviada = requisicao.getWindows(0);
        assertEquals(7, enviada.getChamberId());
        assertEquals(2, enviada.getPolicy().getVersion());
        assertEquals(20, enviada.getPolicy().getMinimumTemperatureTenthsCelsius());
        assertEquals(80, enviada.getPolicy().getMaximumTemperatureTenthsCelsius());
        assertEquals(3, enviada.getPolicy().getConsecutiveUnsafeThreshold());
        assertEquals(List.of("ctx-1", "ctx-2"),
                enviada.getContextList().stream().map(r -> r.getExternalId()).toList());
        assertEquals(List.of("nova-1", "nova-2"),
                enviada.getCandidatesList().stream().map(r -> r.getExternalId()).toList());
        assertEquals(-15, enviada.getContext(0).getTemperatureTenthsCelsius());
        assertTrue(enviada.getContext(1).hasTemperatureTenthsCelsius(), "zero é valor, não ausência");
        assertEquals(0, enviada.getContext(1).getTemperatureTenthsCelsius());
        assertEquals(87, enviada.getCandidates(0).getTemperatureTenthsCelsius());
        assertEquals(9999, enviada.getCandidates(1).getTemperatureTenthsCelsius());
        var medida = enviada.getCandidates(0).getMeasuredAt();
        var utc = instante.toInstant();
        assertEquals(utc.getEpochSecond(), medida.getSeconds());
        assertEquals(123_456_000, medida.getNanos());
        for (var leitura : enviada.getCandidatesList()) {
            assertEquals(7, leitura.getChamberId());
            assertEquals(2, leitura.getPolicyVersion());
        }
    }

    @Test
    void preservaOSinalIntermediarioEOResultadoDeTodasAsCandidatas() {
        var resultado = adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela(1, "9", "9", "9", "4")));

        var candidatas = analisada(resultado, 0).candidatas();
        assertEquals(4, candidatas.size());
        assertEquals(new ResultadoDaCandidata("c1", ResultadoTermico.insegura(1, false), List.of("c1")),
                candidatas.get(0));
        assertEquals(new ResultadoDaCandidata("c2", ResultadoTermico.insegura(2, false), List.of("c1", "c2")),
                candidatas.get(1));
        assertEquals(new ResultadoDaCandidata("c3", ResultadoTermico.insegura(3, true), List.of("c1", "c2", "c3")),
                candidatas.get(2));
        assertEquals(new ResultadoDaCandidata("c4", ResultadoTermico.segura(), List.of()), candidatas.get(3));
    }

    @Test
    void contextoCompletaASequenciaEEvidenciasReferenciamLeiturasAnteriores() {
        var janela = new JanelaDeAnalise(1, POLITICA,
                List.of(leitura("r1", 0, "9"), leitura("r2", 1, "9")),
                List.of(leitura("r3", 2, "9"), leitura("r4", 3, "4")));

        var candidatas = analisada(adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela)), 0)
                .candidatas();

        assertEquals(List.of("r3", "r4"), candidatas.stream().map(ResultadoDaCandidata::idExterno).toList());
        assertEquals(ResultadoTermico.insegura(3, true), candidatas.get(0).resultado());
        assertEquals(List.of("r1", "r2", "r3"), candidatas.get(0).evidencias());
        assertEquals(ClassificacaoTermica.SEGURA, candidatas.get(1).resultado().classificacao());
    }

    static Stream<Arguments> janelasComLeituraSegura() {
        return Stream.of("2", "4", "8").flatMap(temperatura -> Stream.of(
                Arguments.of("segura nas candidatas: " + temperatura, janela(1, "9", temperatura, "9")),
                Arguments.of("segura no contexto: " + temperatura, new JanelaDeAnalise(1, POLITICA,
                        List.of(leitura("c1", 0, "9"), leitura("c2", 1, temperatura)),
                        List.of(leitura("c3", 2, "9"))))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("janelasComLeituraSegura")
    void evidenciasNaoPodemAtravessarLeituraSegura(String cenario, JanelaDeAnalise janela) {
        servidor.comportarSe(ServidorTelemetriaDeTeste.analisando(adulterarAnalise(analise -> {
            int ultima = analise.getCandidatesCount() - 1;
            analise.setCandidates(ultima, analise.getCandidates(ultima).toBuilder()
                    .setConsecutiveUnsafeCount(3).setThresholdReached(true)
                    .clearEvidenceExternalIds().addAllEvidenceExternalIds(List.of("c1", "c2", "c3")));
        })));

        var falha = assertThrows(FalhaDeIntegracao.class,
                () -> adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela)), cenario);

        assertTrue(falha.getMessage().startsWith("Resposta inválida do serviço de análise térmica"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("janelasComLeituraSegura")
    void leituraSeguraReiniciaASequenciaEEvidencias(String cenario, JanelaDeAnalise janela) {
        var candidatas = analisada(adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela)), 0)
                .candidatas();

        assertEquals(new ResultadoDaCandidata("c3", ResultadoTermico.insegura(1, false), List.of("c3")),
                candidatas.getLast(), cenario);
    }

    @Test
    void aoManterOLimiarPreservaSomenteAsUltimasNEvidencias() {
        var candidatas = analisada(adaptador(PRAZO).analisar(
                SolicitacaoDeAnalise.de("corr", janela(1, "9", "1", "9", "1"))), 0).candidatas();

        assertEquals(new ResultadoDaCandidata("c4", ResultadoTermico.insegura(3, true), List.of("c2", "c3", "c4")),
                candidatas.getLast());
    }

    @Test
    void variasJanelasMantemOrdemEErroDeUmaCamaraNaoAnulaAsOutras() {
        servidor.comportarSe(ServidorTelemetriaDeTeste.analisando(resposta -> resposta.toBuilder()
                .setResults(1, WindowResult.newBuilder().setChamberId(20).setPolicyVersion(2)
                        .setError(WindowError.newBuilder()
                                .setCode(WindowErrorCode.WINDOW_ERROR_CODE_CHAMBER_MISMATCH)
                                .setDescription("leitura c1 pertence a outra câmara")))
                .build()));
        var solicitacao = new SolicitacaoDeAnalise("lote-1",
                List.of(janela(10, "9", "1"), janela(20, "5"), janela(30, "4")));

        var resultado = adaptador(PRAZO).analisar(solicitacao);

        assertEquals("lote-1", resultado.correlacaoId());
        assertEquals(List.of(10L, 20L, 30L), resultado.janelas().stream().map(ResultadoDaJanela::camaraId).toList());
        assertEquals(2, analisada(resultado, 0).candidatas().size());
        var rejeitada = assertInstanceOf(ResultadoDaJanela.Rejeitada.class, resultado.janelas().get(1));
        assertEquals(MotivoDaRejeicao.CAMARA_DIVERGENTE, rejeitada.motivo());
        assertEquals("leitura c1 pertence a outra câmara", rejeitada.descricao());
        assertEquals(1, analisada(resultado, 2).candidatas().size());
        assertEquals(1, servidor.chamadas.get(), "uma única RPC para todas as câmaras");
    }

    static Stream<Arguments> respostasForaDoContrato() {
        return Stream.of(
                Arguments.of("correlação trocada", adulterar(r -> r.toBuilder().setCorrelationId("outra").build())),
                Arguments.of("janela faltando", adulterar(r -> r.toBuilder().removeResults(0).build())),
                Arguments.of("câmara trocada", adulterar(r -> r.toBuilder()
                        .setResults(0, r.getResults(0).toBuilder().setChamberId(99)).build())),
                Arguments.of("versão trocada", adulterar(r -> r.toBuilder()
                        .setResults(0, r.getResults(0).toBuilder().setPolicyVersion(9)).build())),
                Arguments.of("sem análise nem erro", adulterar(r -> r.toBuilder()
                        .setResults(0, WindowResult.newBuilder().setChamberId(1).setPolicyVersion(2)).build())),
                Arguments.of("candidata faltando", adulterarAnalise(a -> a.removeCandidates(3))),
                Arguments.of("candidata fora de ordem", adulterarAnalise(a -> a
                        .setCandidates(0, a.getCandidates(1)).setCandidates(1, a.getCandidates(0)))),
                Arguments.of("classificação não especificada", adulterarAnalise(a -> a.setCandidates(3,
                        a.getCandidates(3).toBuilder()
                                .setClassification(ReadingClassification.READING_CLASSIFICATION_UNSPECIFIED)))),
                Arguments.of("classificação desconhecida", adulterarAnalise(a -> a.setCandidates(3,
                        a.getCandidates(3).toBuilder().setClassificationValue(42)))),
                Arguments.of("segura com sequência", adulterarAnalise(a -> a.setCandidates(3,
                        a.getCandidates(3).toBuilder().setConsecutiveUnsafeCount(1)))),
                Arguments.of("segura classificada como insegura", adulterarAnalise(a -> a.setCandidates(3,
                        a.getCandidates(3).toBuilder()
                                .setClassification(ReadingClassification.READING_CLASSIFICATION_UNSAFE)
                                .setConsecutiveUnsafeCount(3).setThresholdReached(true)
                                .addAllEvidenceExternalIds(List.of("c2", "c3", "c4"))))),
                Arguments.of("insegura classificada como segura", adulterarAnalise(a -> a.setCandidates(0,
                        a.getCandidates(0).toBuilder()
                                .setClassification(ReadingClassification.READING_CLASSIFICATION_SAFE)
                                .setConsecutiveUnsafeCount(0).clearEvidenceExternalIds()))),
                Arguments.of("sequência insegura truncada", adulterarAnalise(a -> a.setCandidates(2,
                        a.getCandidates(2).toBuilder().setConsecutiveUnsafeCount(1).setThresholdReached(false)
                                .clearEvidenceExternalIds().addEvidenceExternalIds("c3")))),
                Arguments.of("sequência acima de N", adulterarAnalise(a -> a.setCandidates(2,
                        a.getCandidates(2).toBuilder().setConsecutiveUnsafeCount(4).addEvidenceExternalIds("c3")))),
                Arguments.of("limiar sem sinal", adulterarAnalise(a -> a.setCandidates(2,
                        a.getCandidates(2).toBuilder().setThresholdReached(false)))),
                Arguments.of("sinal antes do limiar", adulterarAnalise(a -> a.setCandidates(0,
                        a.getCandidates(0).toBuilder().setThresholdReached(true)))),
                Arguments.of("evidência desconhecida", adulterarAnalise(a -> a.setCandidates(1,
                        a.getCandidates(1).toBuilder().setEvidenceExternalIds(0, "fantasma")))),
                Arguments.of("evidências sem a candidata", adulterarAnalise(a -> a.setCandidates(1,
                        a.getCandidates(1).toBuilder().setEvidenceExternalIds(1, "c1")))),
                Arguments.of("evidências com leitura futura", adulterarAnalise(a -> a.setCandidates(1,
                        a.getCandidates(1).toBuilder().setEvidenceExternalIds(0, "c3")))),
                Arguments.of("evidências repetidas", adulterarAnalise(a -> a.setCandidates(2,
                        a.getCandidates(2).toBuilder().setEvidenceExternalIds(0, "c3").setEvidenceExternalIds(1, "c3")))),
                Arguments.of("evidências fora de ordem", adulterarAnalise(a -> a.setCandidates(2,
                        a.getCandidates(2).toBuilder().setEvidenceExternalIds(0, "c2").setEvidenceExternalIds(1, "c1")))),
                Arguments.of("erro sem código", adulterar(r -> r.toBuilder().setResults(0, WindowResult.newBuilder()
                        .setChamberId(1).setPolicyVersion(2).setError(WindowError.newBuilder()
                                .setDescription("sem código"))).build())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("respostasForaDoContrato")
    void respostaForaDoContratoViraFalhaDeIntegracaoSemResultadoParcial(String cenario,
                                                                       UnaryOperator<AnalyzeReadingsResponse> erro) {
        servidor.comportarSe(ServidorTelemetriaDeTeste.analisando(erro));

        var falha = assertThrows(FalhaDeIntegracao.class,
                () -> adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela(1, "9", "9", "9", "4"))));

        assertTrue(falha.getMessage().startsWith("Resposta inválida do serviço de análise térmica"), cenario);
        assertEquals(0, falha.getStackTrace().length, "a falha não carrega stack para o chamador");
    }

    static Stream<Arguments> statusDaChamadaInteira() {
        return Stream.of(
                Arguments.of(Status.INVALID_ARGUMENT, FalhaDeIntegracao.class, null),
                Arguments.of(Status.INTERNAL, FalhaDeIntegracao.class, null),
                Arguments.of(Status.UNIMPLEMENTED, FalhaDeIntegracao.class, null),
                Arguments.of(Status.UNAVAILABLE, AnaliseIndisponivel.class, AnaliseIndisponivel.Motivo.SERVICO_INACESSIVEL),
                Arguments.of(Status.DEADLINE_EXCEEDED, AnaliseIndisponivel.class, AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO),
                Arguments.of(Status.CANCELLED, AnaliseIndisponivel.class, AnaliseIndisponivel.Motivo.CANCELADA));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusDaChamadaInteira")
    void traduzStatusRemotoSemRepetirNemRepassarOuLogarADescricaoDoServidor(Status status, Class<?> esperada,
                                                                           AnaliseIndisponivel.Motivo motivo) {
        var segredo = "senha=s3gr3d0\nlinha forjada\r at friovax.Analise.run(Analise.go:42)";
        servidor.comportarSe(ServidorTelemetriaDeTeste.falhando(status.withDescription(segredo)));
        var registros = new ArrayList<ExtLogRecord>();
        var logger = Logger.getLogger(AnaliseTermicaGrpc.class.getName());
        var captura = new Handler() {
            @Override
            public void publish(LogRecord registro) {
                registros.add(ExtLogRecord.wrap(registro));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        RuntimeException falha;
        logger.addHandler(captura);
        try {
            falha = assertThrows(RuntimeException.class,
                    () -> adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela(1, "5"))));
        } finally {
            logger.removeHandler(captura);
        }

        assertInstanceOf(esperada, falha);
        if (falha instanceof AnaliseIndisponivel indisponivel) {
            assertEquals(motivo, indisponivel.motivo());
        }
        assertFalse(falha.getMessage().contains("s3gr3d0"));
        assertFalse(falha.getMessage().contains("Analise.go"));
        assertNull(falha.getCause(), "o status remoto não é encadeado na exceção");
        assertEquals(1, registros.size());
        assertEquals("Análise térmica corr falhou com status gRPC " + status.getCode() + ": " + falha.getMessage(),
                registros.getFirst().getFormattedMessage(), "somente correlação, status e diagnóstico controlado");
        assertNull(registros.getFirst().getThrown(), "o log não carrega a exceção remota");
        assertEquals(1, servidor.chamadas.get(), "sem repetição automática");
    }

    @Test
    void chamadaLentaTerminaNoPrazoConfiguradoECancelaNoServidor() throws InterruptedException {
        servidor.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(5)));

        var inicio = System.nanoTime();
        var falha = assertThrows(AnaliseIndisponivel.class,
                () -> adaptador(PRAZO).analisar(SolicitacaoDeAnalise.de("corr", janela(1, "5"))));
        var decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertEquals(AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO, falha.motivo());
        assertDentroDoPrazo(decorrido, PRAZO);
        assertTrue(servidor.observouCancelamento(Duration.ofSeconds(2)), "o prazo chega ao servidor");
        assertEquals(1, servidor.chamadas.get());
    }

    @Test
    void esperaPorConexaoQueNaoCompletaTambemRespeitaOPrazo() throws Exception {
        try (var buracoNegro = new ServerSocket(0)) {
            var conexoes = CompletableFuture.runAsync(() -> aceitarSemResponder(buracoNegro));
            var canalSemHandshake = canalPara(buracoNegro.getLocalPort());
            try {
                var inicio = System.nanoTime();
                var falha = assertThrows(AnaliseIndisponivel.class, () -> new AnaliseTermicaGrpc(
                        TelemetryServiceGrpc.newBlockingStub(canalSemHandshake), PRAZO)
                        .analisar(SolicitacaoDeAnalise.de("corr", janela(1, "5"))));
                var decorrido = Duration.ofNanos(System.nanoTime() - inicio);

                assertEquals(AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO, falha.motivo());
                assertDentroDoPrazo(decorrido, PRAZO);
            } finally {
                canalSemHandshake.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
                conexoes.cancel(true);
            }
        }
    }

    @Test
    void servicoAusenteFalhaRapidoComoIndisponivel() throws Exception {
        var canalSemServidor = canalPara(PortaSemServico.porta());
        try {
            var inicio = System.nanoTime();
            var falha = assertThrows(AnaliseIndisponivel.class, () -> new AnaliseTermicaGrpc(
                    TelemetryServiceGrpc.newBlockingStub(canalSemServidor), PRAZO)
                    .analisar(SolicitacaoDeAnalise.de("corr", janela(1, "5"))));

            assertEquals(AnaliseIndisponivel.Motivo.SERVICO_INACESSIVEL, falha.motivo());
            assertTrue(Duration.ofNanos(System.nanoTime() - inicio).compareTo(PRAZO) < 0,
                    "conexão recusada não espera o prazo inteiro");
        } finally {
            canalSemServidor.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void interromperQuemChamouCancelaARpcNoServidor() throws Exception {
        servidor.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(5)));
        var capturada = new AtomicReference<RuntimeException>();
        var chamador = Thread.ofVirtual().start(() -> {
            try {
                adaptador(Duration.ofSeconds(5)).analisar(SolicitacaoDeAnalise.de("corr-cancelada", janela(1, "5")));
            } catch (RuntimeException e) {
                capturada.set(e);
            }
        });
        assertTrue(servidor.iniciada.await(2, TimeUnit.SECONDS), "a chamada chegou ao servidor");

        chamador.interrupt();
        chamador.join(Duration.ofSeconds(2));

        var falha = assertInstanceOf(AnaliseIndisponivel.class, capturada.get());
        assertEquals(AnaliseIndisponivel.Motivo.CANCELADA, falha.motivo());
        assertTrue(servidor.observouCancelamento(Duration.ofSeconds(2)), "o servidor percebe o cancelamento");
        assertEquals(List.of("corr-cancelada"), servidor.correlacoesNoMetadado);
    }

    @Test
    void prazoNaoPositivoEhRecusadoNaCriacao() {
        var stub = TelemetryServiceGrpc.newBlockingStub(canal);

        assertThrows(IllegalArgumentException.class, () -> new AnaliseTermicaGrpc(stub, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AnaliseTermicaGrpc(stub, Duration.ofMillis(-1)));
    }

    private AnaliseTermicaGrpc adaptador(Duration prazo) {
        return new AnaliseTermicaGrpc(TelemetryServiceGrpc.newBlockingStub(canal), prazo);
    }

    private static ManagedChannel canalPara(int porta) {
        return ManagedChannelBuilder.forAddress("localhost", porta).usePlaintext().build();
    }

    private static ResultadoDaJanela.Analisada analisada(ResultadoDaAnalise resultado, int indice) {
        return assertInstanceOf(ResultadoDaJanela.Analisada.class, resultado.janelas().get(indice));
    }

    private static void assertDentroDoPrazo(Duration decorrido, Duration prazo) {
        assertTrue(decorrido.compareTo(prazo.minusMillis(50)) >= 0, "esperou o prazo: " + decorrido);
        assertTrue(decorrido.compareTo(prazo.plusMillis(700)) < 0, "terminou perto do prazo: " + decorrido);
    }

    /** Aceita a conexão TCP e nunca envia o handshake HTTP/2: o canal fica esperando conectar. */
    private static void aceitarSemResponder(ServerSocket socket) {
        var abertas = new ArrayList<Socket>();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                abertas.add(socket.accept());
            }
        } catch (IOException fechado) {
            // o teste fechou o socket
        } finally {
            for (var aberta : abertas) {
                try {
                    aberta.close();
                } catch (IOException ignorada) {
                    // encerramento do teste
                }
            }
        }
    }

    private static UnaryOperator<AnalyzeReadingsResponse> adulterar(UnaryOperator<AnalyzeReadingsResponse> f) {
        return f;
    }

    private static UnaryOperator<AnalyzeReadingsResponse> adulterarAnalise(
            Consumer<WindowAnalysis.Builder> alteracao) {
        return resposta -> {
            var analise = resposta.getResults(0).getAnalysis().toBuilder();
            alteracao.accept(analise);
            return resposta.toBuilder().setResults(0, resposta.getResults(0).toBuilder().setAnalysis(analise))
                    .build();
        };
    }
}
