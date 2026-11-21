package br.ufrn.friovax.api.auditoria.dominio;

import java.util.Locale;

public enum RecursoAuditado {
    CAMARA, LOTE, POLITICA_TERMICA, OCORRENCIA;

    public String nomeNoAviso() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RecursoAuditado doAviso(String recurso) {
        return valueOf(recurso.toUpperCase(Locale.ROOT));
    }
}
