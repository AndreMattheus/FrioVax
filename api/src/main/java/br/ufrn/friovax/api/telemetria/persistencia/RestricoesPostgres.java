package br.ufrn.friovax.api.telemetria.persistencia;

import org.postgresql.util.PSQLException;

import java.util.Optional;

final class RestricoesPostgres {
    private RestricoesPostgres() {
    }

    static Optional<String> violada(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return Optional.ofNullable(psql.getServerErrorMessage().getConstraint());
            }
        }
        return Optional.empty();
    }
}
