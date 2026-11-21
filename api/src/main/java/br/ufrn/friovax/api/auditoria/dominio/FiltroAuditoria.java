package br.ufrn.friovax.api.auditoria.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Todos são opcionais e combinados com E lógico.
 *
 * @param de        início do período, inclusivo
 * @param ate       fim do período, inclusivo
 * @param usuarioId id do usuário que fez a alteração
 * @param recurso   tipo do recurso
 * @param recursoId id do recurso; exige {@code recurso}, porque câmaras e lotes têm sequências próprias
 */
public record FiltroAuditoria(OffsetDateTime de, OffsetDateTime ate, UUID usuarioId, RecursoAuditado recurso,
                              Long recursoId) {

    public FiltroAuditoria {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("de deve ser anterior ou igual a ate");
        }
        if (recursoId != null && recurso == null) {
            throw new IllegalArgumentException("recursoId exige recurso");
        }
        if (recursoId != null && recursoId <= 0) {
            throw new IllegalArgumentException("recursoId deve ser positivo");
        }
    }

    public static FiltroAuditoria nenhum() {
        return new FiltroAuditoria(null, null, null, null, null);
    }

    public boolean aceita(EventoDeAuditoria evento) {
        return (de == null || !evento.instante().isBefore(de))
                && (ate == null || !evento.instante().isAfter(ate))
                && (usuarioId == null || usuarioId.equals(evento.ator().id()))
                && (recurso == null || recurso == evento.recurso())
                && (recursoId == null || recursoId == evento.recursoId());
    }
}
