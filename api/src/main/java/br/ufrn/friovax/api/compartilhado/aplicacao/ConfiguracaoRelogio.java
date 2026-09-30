package br.ufrn.friovax.api.compartilhado.aplicacao;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.ZoneId;

@Singleton
public class ConfiguracaoRelogio {

    @Produces
    @Singleton
    public Clock relogio() {
        return Clock.system(ZoneId.of("America/Fortaleza"));
    }
}
