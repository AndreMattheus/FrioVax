package br.ufrn.friovax.api.auditoria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.AuditoriaRepository;
import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;

@ApplicationScoped
public class RegistrarAuditoria {

    private final AuditoriaRepository trilha;
    private final ContextoDaOperacao contexto;

    @Inject
    public RegistrarAuditoria(AuditoriaRepository trilha, ContextoDaOperacao contexto) {
        this.trilha = trilha;
        this.contexto = contexto;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public EventoDeAuditoria registrar(RecursoAuditado recurso, long recursoId, OperacaoAuditada operacao,
                                       String descricao, OffsetDateTime instante) {
        return trilha.registrar(EventoDeAuditoria.novo(instante, contexto.ator(), recurso, recursoId, operacao,
                descricao, contexto.correlacaoId()));
    }
}
