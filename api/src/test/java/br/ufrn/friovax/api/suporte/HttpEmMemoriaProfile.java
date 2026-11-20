package br.ufrn.friovax.api.suporte;

import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

/** Substituições CDI disponíveis somente nos testes que selecionam este perfil. */
public class HttpEmMemoriaProfile implements QuarkusTestProfile {
    public static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-10-11T01:30:00Z"), ZoneId.of("America/Fortaleza"));

    /** Porta local sem serviço: estes testes rodam sem o Go, como a API deve suportar. */
    public static final int PORTA_SEM_TELEMETRIA = PortaSemServico.porta();

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.devservices.enabled", "false",
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.jdbc", "false",
                "quarkus.datasource.active", "false",
                "quarkus.hibernate-orm.enabled", "false",
                "quarkus.flyway.enabled", "false",
                "quarkus.flyway.migrate-at-start", "false",
                "quarkus.grpc.clients.telemetria.test-port", String.valueOf(PORTA_SEM_TELEMETRIA));
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public CamaraRepositoryEmMemoria camaras() {
        return new CamaraRepositoryEmMemoria();
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public LoteRepositoryEmMemoria lotes() {
        return new LoteRepositoryEmMemoria();
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public LeituraRepositoryEmMemoria leituras() {
        return new LeituraRepositoryEmMemoria();
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public OcorrenciaRepositoryEmMemoria ocorrencias() {
        return new OcorrenciaRepositoryEmMemoria();
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public Clock relogio() {
        return RELOGIO;
    }
}
