package br.ufrn.friovax.api.telemetria.persistencia;

import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Identifica a aplicação Quarkus em execução nos testes. Cada inicialização cria um novo contêiner de beans e,
 * portanto, outra identidade: se a identidade mudou, a aplicação foi de fato reiniciada.
 */
@Singleton
public class IdentidadeDaAplicacao {
    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
