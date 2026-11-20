package br.ufrn.friovax.api.telemetria.integracao;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.time.Duration;
import java.util.Map;

/** Sobe o servidor gRPC de teste antes do Quarkus e aponta o cliente configurado para ele. */
public class ServidorTelemetriaResource implements QuarkusTestResourceLifecycleManager {

    public static final Duration PRAZO = Duration.ofMillis(400);
    public static volatile ServidorTelemetriaDeTeste servidor;

    @Override
    public Map<String, String> start() {
        servidor = new ServidorTelemetriaDeTeste();
        return Map.of(
                "quarkus.grpc.clients.telemetria.host", "localhost",
                "quarkus.grpc.clients.telemetria.test-port", String.valueOf(servidor.porta()),
                "friovax.telemetria.prazo", PRAZO.toMillis() + "ms");
    }

    @Override
    public void stop() {
        try {
            servidor.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
