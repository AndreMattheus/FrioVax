package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.PoliticaDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import br.ufrn.friovax.telemetry.v1.AnalyzeReadingsRequest;
import br.ufrn.friovax.telemetry.v1.Reading;
import br.ufrn.friovax.telemetry.v1.ReadingWindow;
import br.ufrn.friovax.telemetry.v1.ThermalPolicy;
import com.google.protobuf.Timestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Converte a solicitação do domínio na mensagem Protobuf, sem arredondar temperaturas nem instantes. */
final class RequisicaoGrpc {

    private RequisicaoGrpc() {
    }

    static AnalyzeReadingsRequest converter(SolicitacaoDeAnalise solicitacao) {
        var requisicao = AnalyzeReadingsRequest.newBuilder().setCorrelationId(solicitacao.correlacaoId());
        for (var janela : solicitacao.janelas()) {
            requisicao.addWindows(janela(janela));
        }
        return requisicao.build();
    }

    /** 8,7 °C vira 87 e -1,5 °C vira -15. O domínio garante no máximo uma casa decimal. */
    static int emDecimos(BigDecimal temperatura) {
        return temperatura.movePointRight(1).intValueExact();
    }

    /** Mesmo instante em segundos e nanos desde a época UTC; o domínio garante nanos múltiplo de 1000. */
    static Timestamp timestamp(OffsetDateTime instante) {
        var utc = instante.toInstant();
        return Timestamp.newBuilder().setSeconds(utc.getEpochSecond()).setNanos(utc.getNano()).build();
    }

    private static ReadingWindow janela(JanelaDeAnalise janela) {
        var politica = janela.politica();
        var mensagem = ReadingWindow.newBuilder()
                .setChamberId(janela.camaraId())
                .setPolicy(politica(politica));
        for (var leitura : janela.contexto()) {
            mensagem.addContext(leitura(janela.camaraId(), politica.versao(), leitura));
        }
        for (var leitura : janela.candidatas()) {
            mensagem.addCandidates(leitura(janela.camaraId(), politica.versao(), leitura));
        }
        return mensagem.build();
    }

    private static ThermalPolicy politica(PoliticaDaAnalise politica) {
        return ThermalPolicy.newBuilder()
                .setVersion(politica.versao())
                .setMinimumTemperatureTenthsCelsius(emDecimos(politica.temperaturaMinima()))
                .setMaximumTemperatureTenthsCelsius(emDecimos(politica.temperaturaMaxima()))
                .setConsecutiveUnsafeThreshold(politica.limiar())
                .build();
    }

    private static Reading leitura(long camaraId, int politicaVersao, LeituraDaAnalise leitura) {
        return Reading.newBuilder()
                .setExternalId(leitura.idExterno())
                .setChamberId(camaraId)
                .setPolicyVersion(politicaVersao)
                .setTemperatureTenthsCelsius(emDecimos(leitura.temperatura()))
                .setMeasuredAt(timestamp(leitura.instante()))
                .build();
    }
}
