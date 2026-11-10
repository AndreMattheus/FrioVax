package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel.Motivo;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import br.ufrn.friovax.telemetry.v1.TelemetryServiceGrpc.TelemetryServiceBlockingStub;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador gRPC da porta {@link AnaliseTermica}, sobre os stubs gerados de {@code telemetry.proto}.
 *
 * <p>Cada chamada recebe o prazo configurado (padrão 800 ms), que também limita a espera pela conexão; não há
 * repetição automática. O canal é criado sem conectar, então a API inicia mesmo com o serviço Go ausente. A
 * correlação segue no corpo e no metadado {@value #CABECALHO_CORRELACAO}; interromper a thread chamadora cancela a RPC
 * no servidor.
 */
@ApplicationScoped
public class AnaliseTermicaGrpc implements AnaliseTermica {

    static final String CABECALHO_CORRELACAO = "x-correlation-id";
    static final Metadata.Key<String> CHAVE_CORRELACAO =
            Metadata.Key.of(CABECALHO_CORRELACAO, Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger LOG = Logger.getLogger(AnaliseTermicaGrpc.class);

    private final TelemetryServiceBlockingStub stub;
    private final Duration prazo;

    @Inject
    public AnaliseTermicaGrpc(@GrpcClient("telemetria") TelemetryServiceBlockingStub stub,
                              ConfiguracaoTelemetria configuracao) {
        this(stub, configuracao.prazo());
    }

    AnaliseTermicaGrpc(TelemetryServiceBlockingStub stub, Duration prazo) {
        this.stub = Objects.requireNonNull(stub, "stub");
        this.prazo = Objects.requireNonNull(prazo, "prazo");
        if (prazo.isNegative() || prazo.isZero()) {
            throw new IllegalArgumentException("O prazo da análise térmica deve ser positivo.");
        }
    }

    @Override
    public ResultadoDaAnalise analisar(SolicitacaoDeAnalise solicitacao) {
        Objects.requireNonNull(solicitacao, "solicitacao");
        var requisicao = RequisicaoGrpc.converter(solicitacao);
        var metadados = new Metadata();
        metadados.put(CHAVE_CORRELACAO, solicitacao.correlacaoId());
        try {
            var resposta = stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadados))
                    .withDeadlineAfter(prazo.toNanos(), TimeUnit.NANOSECONDS)
                    .analyzeReadings(requisicao);
            return RespostaGrpc.converter(solicitacao, resposta);
        } catch (StatusRuntimeException falha) {
            throw traduzir(solicitacao.correlacaoId(), falha);
        } catch (FalhaDeIntegracao falha) {
            LOG.warnf("Análise térmica %s: %s", solicitacao.correlacaoId(), falha.getMessage());
            throw falha;
        }
    }

    /** Traduz o status remoto sem repassar a descrição do servidor nem a causa ao chamador. */
    private RuntimeException traduzir(String correlacaoId, StatusRuntimeException falha) {
        var codigo = falha.getStatus().getCode();
        LOG.warnf("Análise térmica %s falhou com status gRPC %s", correlacaoId, codigo);
        return switch (codigo) {
            case UNAVAILABLE -> new AnaliseIndisponivel(Motivo.SERVICO_INACESSIVEL);
            case DEADLINE_EXCEEDED -> new AnaliseIndisponivel(Motivo.PRAZO_ESGOTADO);
            case CANCELLED -> new AnaliseIndisponivel(Motivo.CANCELADA);
            default -> new FalhaDeIntegracao("O serviço de análise térmica recusou a chamada (status " + codigo + ").");
        };
    }
}
