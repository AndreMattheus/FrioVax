package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static br.ufrn.friovax.api.telemetria.integracao.JanelasDeTeste.janela;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O adaptador injetado pelo Quarkus usa endereço, porta e prazo da configuração. */
@QuarkusTest
@TestProfile(TelemetriaGrpcProfile.class)
class AnaliseTermicaGrpcQuarkusTest {

    @Inject
    AnaliseTermica analise;

    @BeforeEach
    void limpar() {
        ServidorTelemetriaResource.servidor.reiniciarRegistros();
    }

    @Test
    void portaInjetadaChamaOServidorConfigurado() {
        var resultado = analise.analisar(SolicitacaoDeAnalise.de("quarkus-1", janela(5, "9", "9", "9", "4")));

        var janela = assertInstanceOf(ResultadoDaJanela.Analisada.class, resultado.janelas().getFirst());
        assertEquals(4, janela.candidatas().size());
        assertTrue(janela.candidatas().get(2).resultado().limiarAtingido());
        assertEquals("quarkus-1", ServidorTelemetriaResource.servidor.ultimaRequisicao().getCorrelationId());
    }

    @Test
    void prazoConfiguradoPorAmbienteEncerraAChamadaLenta() {
        ServidorTelemetriaResource.servidor.comportarSe(ServidorTelemetriaDeTeste.demorando(Duration.ofSeconds(5)));
        var prazo = ServidorTelemetriaResource.PRAZO;

        var inicio = System.nanoTime();
        var falha = assertThrows(AnaliseIndisponivel.class,
                () -> analise.analisar(SolicitacaoDeAnalise.de("quarkus-2", janela(5, "5"))));
        var decorrido = Duration.ofNanos(System.nanoTime() - inicio);

        assertEquals(AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO, falha.motivo());
        assertTrue(decorrido.compareTo(prazo.minusMillis(50)) >= 0, "esperou o prazo: " + decorrido);
        assertTrue(decorrido.compareTo(prazo.plusMillis(300)) < 0, "não usou o padrão de 800 ms: " + decorrido);
    }
}
