package br.ufrn.friovax.api.telemetria.integracao;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Configuração do cliente de análise térmica. Endereço e porta ficam em {@code quarkus.grpc.clients.telemetria.*};
 * aqui fica o prazo aplicado a cada chamada.
 */
@ConfigMapping(prefix = "friovax.telemetria")
public interface ConfiguracaoTelemetria {

    /** Prazo de cada RPC, incluindo a espera pela conexão (ADR 0003, seção 7). */
    @WithDefault("800ms")
    Duration prazo();
}
