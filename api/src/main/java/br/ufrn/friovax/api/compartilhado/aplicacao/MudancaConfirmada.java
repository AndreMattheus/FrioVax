package br.ufrn.friovax.api.compartilhado.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aviso interno de que uma alteração foi gravada com sucesso. Só é entregue aos observadores depois do commit da
 * transação: se a gravação for desfeita, o aviso não sai.
 *
 * <p>Quando tem {@code operacao}, a mudança também entra na trilha de auditoria (US10), gravada pelo publicador na
 * mesma transação da alteração. Avisos sem operação (como os da persistência de ocorrências) só invalidam projeções.
 *
 * @param recurso   tipo do que mudou, como {@code "camara"}
 * @param recursoId identificador técnico do que mudou
 * @param descricao resumo legível da mudança, gerado pela aplicação (nunca o body da requisição)
 * @param autorId   quem fez a mudança, quando identificado
 * @param instante  momento oficial da mudança
 * @param operacao  operação auditada, ou nulo quando a mudança não entra na trilha
 */
public record MudancaConfirmada(String recurso, long recursoId, String descricao, UUID autorId,
                                OffsetDateTime instante, OperacaoAuditada operacao) {

    public MudancaConfirmada {
        Objects.requireNonNull(recurso, "recurso");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(instante, "instante");
    }

    /** Mudança que só invalida projeções, sem entrar na trilha. */
    public MudancaConfirmada(String recurso, long recursoId, String descricao, UUID autorId, OffsetDateTime instante) {
        this(recurso, recursoId, descricao, autorId, instante, null);
    }

    /** Mudança de um recurso auditado: o publicador grava o evento de auditoria na mesma transação. */
    public static MudancaConfirmada auditada(RecursoAuditado recurso, long recursoId, OperacaoAuditada operacao,
                                             String descricao, OffsetDateTime instante) {
        return new MudancaConfirmada(recurso.nomeNoAviso(), recursoId, descricao, null, instante,
                Objects.requireNonNull(operacao, "operacao"));
    }

    public Optional<UUID> autor() {
        return Optional.ofNullable(autorId);
    }
    public boolean auditavel() {
        return operacao != null;
    }
}
