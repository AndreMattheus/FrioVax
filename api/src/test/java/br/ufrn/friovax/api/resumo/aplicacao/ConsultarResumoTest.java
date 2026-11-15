package br.ufrn.friovax.api.resumo.aplicacao;

import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.resumo.dominio.TotaisResumo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarResumoTest {
    private final Relogio relogio = new Relogio();
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private final AtomicInteger consultas = new AtomicInteger();
    private final TotaisResumo totais = new TotaisResumo(Map.of(), Map.of(), Map.of(), 0, 0);
    private final ConsultarResumo resumo = new ConsultarResumo(() -> {
        consultas.incrementAndGet();
        return totais;
    }, relogio, Duration.ofSeconds(30), metricas);

    @Test
    void expiraExatamenteNoTtlComContadoresObservaveis() {
        var inicial = resumo.consultar();
        assertEquals(relogio.instant().plusSeconds(30), inicial.validade());
        relogio.agora = relogio.agora.plusSeconds(29);
        assertSame(inicial, resumo.consultar());
        relogio.agora = relogio.agora.plusSeconds(1);
        var novo = resumo.consultar();
        assertNotSame(inicial, novo);
        assertEquals(2, consultas.get());
        assertEquals(1, metricas.get("friovax_resumo_cache_hits_total").counter().count());
        assertEquals(2, metricas.get("friovax_resumo_cache_misses_total").counter().count());
    }

    @Test
    void invalidaSomenteRecursosRelevantes() {
        var anterior = resumo.consultar();
        resumo.invalidar(evento("usuario"));
        assertSame(anterior, resumo.consultar());
        for (var recurso : new String[]{"camara", "lote", "leitura", "ocorrencia"}) {
            resumo.invalidar(evento(recurso));
            var novo = resumo.consultar();
            assertNotSame(anterior, novo);
            anterior = novo;
        }
    }

    @Test
    void consultasConcorrentesCompartilhamUmaCarga() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tarefas = java.util.stream.IntStream.range(0, 16)
                    .mapToObj(i -> (Callable<Resumo>) resumo::consultar).toList();
            var respostas = executor.invokeAll(tarefas);
            var primeira = respostas.getFirst().get();
            for (var resposta : respostas) assertSame(primeira, resposta.get());
        }
        assertEquals(1, consultas.get());
    }

    @Test
    void falhaNaoPreencheCacheETtlDeveSerPositivo() {
        var tentativas = new AtomicInteger();
        var servico = new ConsultarResumo(() -> {
            if (tentativas.incrementAndGet() == 1) throw new IllegalStateException("banco indisponível");
            return totais;
        }, relogio, Duration.ofSeconds(30), metricas);
        assertThrows(IllegalStateException.class, servico::consultar);
        assertEquals(totais, servico.consultar().totais());
        assertThrows(IllegalArgumentException.class, () ->
                new ConsultarResumo(() -> totais, relogio, Duration.ZERO, metricas));
    }

    private MudancaConfirmada evento(String recurso) {
        return new MudancaConfirmada(recurso, 1, "Teste", null, OffsetDateTime.now(relogio));
    }

    static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-15T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(agora, zone); }
        public Instant instant() { return agora; }
    }
}
