package br.ufrn.friovax.api.camara.dominio;

/**
 * Estado operacional atribuído manualmente à câmara (contrato, §3.1).
 */
public enum EstadoCamara {
    OPERACIONAL,
    MANUTENCAO,
    DESATIVADA;

    public boolean aceitaLotes() {
        return this == OPERACIONAL;
    }
}
