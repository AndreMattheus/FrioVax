package br.ufrn.friovax.api.suporte;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

/** Cada cenário começa com dados e sequência de IDs novos. */
public abstract class HttpEmMemoriaSupport {
    @Inject
    protected CamaraRepositoryEmMemoria camaras;

    @Inject
    protected LoteRepositoryEmMemoria lotes;

    @Inject
    protected AuditoriaRepositoryEmMemoria auditoria;

    @BeforeEach
    void limparRepositorios() {
        lotes.limpar();
        camaras.limpar();
        auditoria.limpar();
    }
}
