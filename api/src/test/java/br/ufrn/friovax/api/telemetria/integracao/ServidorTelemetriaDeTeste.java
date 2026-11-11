package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.telemetry.v1.AnalyzeReadingsRequest;
import br.ufrn.friovax.telemetry.v1.AnalyzeReadingsResponse;
import br.ufrn.friovax.telemetry.v1.CandidateResult;
import br.ufrn.friovax.telemetry.v1.Reading;
import br.ufrn.friovax.telemetry.v1.ReadingClassification;
import br.ufrn.friovax.telemetry.v1.ReadingWindow;
import br.ufrn.friovax.telemetry.v1.TelemetryServiceGrpc;
import br.ufrn.friovax.telemetry.v1.WindowAnalysis;
import br.ufrn.friovax.telemetry.v1.WindowResult;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Servidor gRPC real, com transporte Netty numa porta local livre, que substitui o serviço Go nos testes. Por padrão
 * analisa as janelas como o contrato descreve; cada teste pode trocar o comportamento para demorar, falhar com um
 * status ou adulterar a resposta. Registra as requisições, a correlação recebida no metadado e os cancelamentos.
 */
final class ServidorTelemetriaDeTeste implements AutoCloseable {

    @FunctionalInterface
    interface Comportamento {
        void atender(AnalyzeReadingsRequest requisicao, StreamObserver<AnalyzeReadingsResponse> resposta)
                throws InterruptedException;
    }

    private static final Context.Key<String> CORRELACAO = Context.key("correlacao");

    private final Server servidor;
    private volatile Comportamento comportamento = analisando(UnaryOperator.identity());

    final List<AnalyzeReadingsRequest> requisicoes = new CopyOnWriteArrayList<>();
    final List<String> correlacoesNoMetadado = new CopyOnWriteArrayList<>();
    final AtomicInteger chamadas = new AtomicInteger();
    volatile CountDownLatch iniciada = new CountDownLatch(1);
    volatile CountDownLatch cancelada = new CountDownLatch(1);

    ServidorTelemetriaDeTeste() {
        var servico = new TelemetryServiceGrpc.TelemetryServiceImplBase() {
            @Override
            public void analyzeReadings(AnalyzeReadingsRequest requisicao,
                                        StreamObserver<AnalyzeReadingsResponse> resposta) {
                chamadas.incrementAndGet();
                requisicoes.add(requisicao);
                correlacoesNoMetadado.add(CORRELACAO.get());
                Context.current().addListener(contexto -> cancelada.countDown(), Runnable::run);
                iniciada.countDown();
                try {
                    comportamento.atender(requisicao, resposta);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        ServerInterceptor capturaCorrelacao = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata metadados,
                                                               ServerCallHandler<Q, R> proximo) {
                var contexto = Context.current().withValue(CORRELACAO,
                        metadados.get(AnaliseTermicaGrpc.CHAVE_CORRELACAO));
                return Contexts.interceptCall(contexto, call, metadados, proximo);
            }
        };
        try {
            servidor = NettyServerBuilder.forAddress(new InetSocketAddress("localhost", 0))
                    .addService(ServerInterceptors.intercept(servico, capturaCorrelacao))
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    int porta() {
        return servidor.getPort();
    }

    void comportarSe(Comportamento novo) {
        comportamento = novo;
    }

    void reiniciarRegistros() {
        requisicoes.clear();
        correlacoesNoMetadado.clear();
        chamadas.set(0);
        iniciada = new CountDownLatch(1);
        cancelada = new CountDownLatch(1);
        comportamento = analisando(UnaryOperator.identity());
    }

    AnalyzeReadingsRequest ultimaRequisicao() {
        return requisicoes.getLast();
    }

    boolean observouCancelamento(Duration espera) throws InterruptedException {
        return cancelada.await(espera.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Responde com a análise de referência, opcionalmente adulterada pelo teste. */
    static Comportamento analisando(UnaryOperator<AnalyzeReadingsResponse> adulteracao) {
        return (requisicao, resposta) -> {
            resposta.onNext(adulteracao.apply(analisar(requisicao)));
            resposta.onCompleted();
        };
    }

    static Comportamento falhando(Status status) {
        return (requisicao, resposta) -> resposta.onError(status.asRuntimeException());
    }

    /** Espera sem responder até o cliente cancelar (prazo ou interrupção) ou o tempo acabar. */
    static Comportamento demorando(Duration demora) {
        return (requisicao, resposta) -> {
            long limite = System.nanoTime() + demora.toNanos();
            while (!Context.current().isCancelled() && System.nanoTime() < limite) {
                Thread.sleep(10);
            }
            if (!Context.current().isCancelled()) {
                resposta.onNext(analisar(requisicao));
                resposta.onCompleted();
            }
        };
    }

    /**
     * Análise de referência do contrato: faixa inclusiva, sequência insegura limitada a N, sinal ao atingir ou manter
     * N e evidências das últimas inseguras consecutivas. Existe só para o teste ter respostas realistas.
     */
    static AnalyzeReadingsResponse analisar(AnalyzeReadingsRequest requisicao) {
        var resposta = AnalyzeReadingsResponse.newBuilder().setCorrelationId(requisicao.getCorrelationId());
        for (var janela : requisicao.getWindowsList()) {
            resposta.addResults(WindowResult.newBuilder()
                    .setChamberId(janela.getChamberId())
                    .setPolicyVersion(janela.getPolicy().getVersion())
                    .setAnalysis(analisar(janela)));
        }
        return resposta.build();
    }

    private static WindowAnalysis analisar(ReadingWindow janela) {
        var politica = janela.getPolicy();
        int limiar = politica.getConsecutiveUnsafeThreshold();
        var inseguras = new ArrayDeque<String>();
        for (var leitura : janela.getContextList()) {
            acumular(inseguras, leitura, janela, limiar);
        }
        var analise = WindowAnalysis.newBuilder();
        for (var leitura : janela.getCandidatesList()) {
            boolean insegura = acumular(inseguras, leitura, janela, limiar);
            analise.addCandidates(CandidateResult.newBuilder()
                    .setExternalId(leitura.getExternalId())
                    .setClassification(insegura ? ReadingClassification.READING_CLASSIFICATION_UNSAFE
                            : ReadingClassification.READING_CLASSIFICATION_SAFE)
                    .setConsecutiveUnsafeCount(inseguras.size())
                    .setThresholdReached(insegura && inseguras.size() == limiar)
                    .addAllEvidenceExternalIds(inseguras));
        }
        return analise.build();
    }

    private static boolean acumular(ArrayDeque<String> inseguras, Reading leitura, ReadingWindow janela,
                                    int limiar) {
        var politica = janela.getPolicy();
        int temperatura = leitura.getTemperatureTenthsCelsius();
        boolean insegura = temperatura < politica.getMinimumTemperatureTenthsCelsius()
                || temperatura > politica.getMaximumTemperatureTenthsCelsius();
        if (!insegura) {
            inseguras.clear();
            return false;
        }
        inseguras.addLast(leitura.getExternalId());
        if (inseguras.size() > limiar) {
            inseguras.removeFirst();
        }
        return true;
    }

    @Override
    public void close() throws InterruptedException {
        servidor.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
}
