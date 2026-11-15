package br.ufrn.friovax.api.compartilhado.dominio;

import java.time.OffsetDateTime;

/** Porta para adaptadores notificarem gravações, com entrega somente após commit. */
public interface PublicadorDeMudancaPersistida {
    void publicar(String recurso, long id, String descricao, OffsetDateTime instante);
}
