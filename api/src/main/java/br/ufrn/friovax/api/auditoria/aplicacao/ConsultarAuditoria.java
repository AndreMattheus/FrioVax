package br.ufrn.friovax.api.auditoria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.AuditoriaRepository;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarAuditoria {
    private final AuditoriaRepository auditoria;

    @Inject
    public ConsultarAuditoria(AuditoriaRepository auditoria) {
        this.auditoria = auditoria;
    }

    @Transactional
    public Pagina<EventoDeAuditoria> listar(FiltroAuditoria filtro, Paginacao paginacao) {
        return auditoria.listar(filtro, paginacao);
    }
}
