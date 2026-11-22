package br.ufrn.friovax.api.telemetria.api;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class HistoricoSemGoProfile implements QuarkusTestProfile {
    @Override
    public java.util.Set<Class<?>> getEnabledAlternatives() {
        return java.util.Set.of(AnaliseProibidaNoHistorico.class);
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.grpc.clients.telemetria.host", "localhost",
                "quarkus.grpc.clients.telemetria.port", "1",
                "quarkus.grpc.clients.telemetria.test-port", "1");
    }
}
