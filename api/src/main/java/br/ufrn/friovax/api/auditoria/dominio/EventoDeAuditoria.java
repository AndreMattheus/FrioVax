package br.ufrn.friovax.api.auditoria.dominio;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registro imutável de uma alteração confirmada: quem, quando, em qual recurso, qual operação e com qual correlação.
 * @param id          identificador técnico; nulo antes de gravar
 * @param instante    momento oficial da alteração, em UTC e com precisão de microssegundos (a do PostgreSQL)
 * @param correlacaoId identificador da requisição que causou a alteração (cabeçalho {@code X-Correlation-Id} ou
 *                    gerado pelo servidor)
 */
public record EventoDeAuditoria(Long id, OffsetDateTime instante, Ator ator, RecursoAuditado recurso, long recursoId,
                                OperacaoAuditada operacao, String descricao, String correlacaoId) {

    public static final int DESCRICAO_MAXIMA = 500;
    public static final int CORRELACAO_MAXIMA = 128;
    /** Mesmo formato aceito pela análise térmica e pelo serviço Go (ADR 0003). */
    public static final Pattern CORRELACAO = Pattern.compile("[A-Za-z0-9._:-]{1," + CORRELACAO_MAXIMA + "}");

    public EventoDeAuditoria {
        Objects.requireNonNull(instante, "instante");
        Objects.requireNonNull(ator, "ator");
        Objects.requireNonNull(recurso, "recurso");
        Objects.requireNonNull(operacao, "operacao");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(correlacaoId, "correlacaoId");
        instante = instante.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (recursoId <= 0) {
            throw new IllegalArgumentException("O id do recurso auditado deve ser positivo");
        }
        if (descricao.isBlank() || descricao.length() > DESCRICAO_MAXIMA) {
            throw new IllegalArgumentException("A descrição deve ter de 1 a " + DESCRICAO_MAXIMA + " caracteres");
        }
        if (!correlacaoValida(correlacaoId)) {
            throw new IllegalArgumentException("Correlação fora do formato aceito");
        }
    }

    public static EventoDeAuditoria novo(OffsetDateTime instante, Ator ator, RecursoAuditado recurso, long recursoId,
                                         OperacaoAuditada operacao, String descricao, String correlacaoId) {
        return new EventoDeAuditoria(null, instante, ator, recurso, recursoId, operacao, descricao, correlacaoId);
    }

    public static boolean correlacaoValida(String correlacaoId) {
        return correlacaoId != null && CORRELACAO.matcher(correlacaoId).matches();
    }

    public EventoDeAuditoria comId(long novoId) {
        if (id != null) {
            throw new IllegalStateException("O evento de auditoria já foi gravado com id " + id);
        }
        return new EventoDeAuditoria(novoId, instante, ator, recurso, recursoId, operacao, descricao, correlacaoId);
    }
}
