package br.ufrn.friovax.api.auditoria.persistencia;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.AuditoriaRepository;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AuditoriaRepositoryPostgres implements AuditoriaRepository,
        PanacheRepositoryBase<EventoDeAuditoriaEntity, Long> {

    @Override
    public EventoDeAuditoria registrar(EventoDeAuditoria evento) {
        if (evento.id() != null) {
            throw new IllegalStateException("Eventos de auditoria não são alterados depois de gravados");
        }
        var entity = new EventoDeAuditoriaEntity();
        entity.instante = evento.instante();
        entity.atorId = evento.ator().id();
        entity.atorLogin = evento.ator().login();
        entity.atorPerfil = evento.ator().perfil();
        entity.recurso = evento.recurso();
        entity.recursoId = evento.recursoId();
        entity.operacao = evento.operacao();
        entity.descricao = evento.descricao();
        entity.correlacaoId = evento.correlacaoId();
        persist(entity);
        return evento.comId(entity.id);
    }

    @Override
    public Pagina<EventoDeAuditoria> listar(FiltroAuditoria filtro, Paginacao paginacao) {
        var condicoes = new StringBuilder("id > 0");
        Map<String, Object> params = new HashMap<>();
        if (filtro.de() != null) {
            condicoes.append(" and instante >= :de");
            params.put("de", filtro.de());
        }
        if (filtro.ate() != null) {
            condicoes.append(" and instante <= :ate");
            params.put("ate", filtro.ate());
        }
        if (filtro.usuarioId() != null) {
            condicoes.append(" and atorId = :usuarioId");
            params.put("usuarioId", filtro.usuarioId());
        }
        if (filtro.recurso() != null) {
            condicoes.append(" and recurso = :recurso");
            params.put("recurso", filtro.recurso());
        }
        if (filtro.recursoId() != null) {
            condicoes.append(" and recursoId = :recursoId");
            params.put("recursoId", filtro.recursoId());
        }

        var query = find(condicoes.toString(), Sort.by("id"), params);

        List<EventoDeAuditoria> itens = paginacao.deslocamento() > Integer.MAX_VALUE
            ? List.of()
            : query.page(paginacao.pagina(), paginacao.tamanho()).list().stream()
                .map(AuditoriaRepositoryPostgres::paraDominio)
                .toList();

        return Pagina.de(itens, paginacao, query.count());
    }

    private static EventoDeAuditoria paraDominio(EventoDeAuditoriaEntity entity) {
        return new EventoDeAuditoria(entity.id, entity.instante,
                new Ator(entity.atorId, entity.atorLogin, entity.atorPerfil),
                entity.recurso, entity.recursoId, entity.operacao, entity.descricao, entity.correlacaoId);
    }
}
