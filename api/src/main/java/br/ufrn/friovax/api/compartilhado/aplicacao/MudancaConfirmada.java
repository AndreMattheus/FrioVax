package br.ufrn.friovax.api.compartilhado.aplicacao;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aviso interno de que uma alteração foi gravada com sucesso. Só é entregue aos observadores depois do commit da
 * transação: se a gravação for desfeita, o aviso não sai. Servirá de base para a auditoria (US10).
 *
 * @param recurso   tipo do que mudou, como {@code "camara"}
 * @param recursoId identificador técnico do que mudou
 * @param descricao resumo legível da mudança
 * @param autorId   quem fez a mudança, quando identificado
 * @param instante  momento oficial da mudança
 */
public record MudancaConfirmada(String recurso, long recursoId, String descricao, UUID autorId,
                                OffsetDateTime instante) {

    public MudancaConfirmada {
        Objects.requireNonNull(recurso, "recurso");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(instante, "instante");
    }

    public Optional<UUID> autor() {
        return Optional.ofNullable(autorId);
    }
}
