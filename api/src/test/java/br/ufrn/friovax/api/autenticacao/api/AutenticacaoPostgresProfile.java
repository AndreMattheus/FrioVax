package br.ufrn.friovax.api.autenticacao.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.*;
import java.util.Map;

public class AutenticacaoPostgresProfile implements QuarkusTestProfile {
    public static final RelogioControlado RELOGIO = new RelogioControlado();
    public static final String SENHA = "senha-apenas-para-testes";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("friovax.auth.demo.enabled", "true",
                "friovax.auth.demo.administrador", SENHA,
                "friovax.auth.demo.operador", SENHA,
                "friovax.auth.demo.auditor", SENHA,
                "quarkus.hibernate-orm.statistics", "true");
    }

    @Produces @Singleton @Alternative @Priority(1)
    public Clock relogio() { return RELOGIO; }

    public static class RelogioControlado extends Clock {
        public volatile Instant agora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(agora, zone); }
        public Instant instant() { return agora; }
    }
}
