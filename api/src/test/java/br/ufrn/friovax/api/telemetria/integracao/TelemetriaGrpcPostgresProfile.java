package br.ufrn.friovax.api.telemetria.integracao;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.time.Duration;
import java.util.List;

/**
 * PostgreSQL real (Dev Services) com o cliente gRPC apontado para o servidor de teste. O prazo é maior que o do perfil
 * em memória para que o trabalho feito no banco durante a "RPC" caiba nele mesmo num CI lento.
 */
public class TelemetriaGrpcPostgresProfile implements QuarkusTestProfile {

    public static final Duration PRAZO = Duration.ofMillis(1500);

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(Servidor.class));
    }

    public static class Servidor extends ServidorTelemetriaResource {
        @Override
        protected Duration prazo() {
            return PRAZO;
        }
    }
}
