package br.ufrn.friovax.api.resumo.aplicacao;

import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.resumo.dominio.ResumoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

@ApplicationScoped
@Startup
public class ConsultarResumo {
    private final ResumoRepository repositorio;
    private final Clock relogio;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;
    private Resumo cache;

    @Inject
    public ConsultarResumo(ResumoRepository repositorio, Clock relogio,
                           @ConfigProperty(name = "friovax.resumo.ttl", defaultValue = "30s") Duration ttl,
                           MeterRegistry metricas) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("TTL deve ser positivo");
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.ttl = ttl;
        hits = metricas.counter("friovax_resumo_cache_hits_total");
        misses = metricas.counter("friovax_resumo_cache_misses_total");
    }

    // Suspender a transação do chamador impede armazenar dados ainda não confirmados.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public synchronized Resumo consultar() {
        var agora = relogio.instant();
        if (cache != null && agora.isBefore(cache.validade())) {
            hits.increment();
            return cache;
        }
        misses.increment();
        var totais = repositorio.consultar();
        cache = new Resumo(totais, agora, agora.plus(ttl));
        return cache;
    }

    // O publicador dispara somente após commit. O mesmo monitor impede que uma consulta
    // concorrente reponha um resultado anterior à invalidação. Uma nova versão de política
    // volta o estado térmico da câmara a SEM_LEITURAS, por isso também invalida (S2-14).
    public synchronized void invalidar(@Observes MudancaConfirmada mudanca) {
        if (Set.of("camara", "politica_termica", "lote", "leitura", "ocorrencia").contains(mudanca.recurso())) {
            cache = null;
        }
    }
}
