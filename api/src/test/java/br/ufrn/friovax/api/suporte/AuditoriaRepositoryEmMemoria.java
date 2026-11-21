package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.auditoria.dominio.AuditoriaRepository;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Trilha em memória para testes sem banco: só grava e consulta, como a porta. */
public class AuditoriaRepositoryEmMemoria implements AuditoriaRepository {
    private final List<EventoDeAuditoria> eventos = new ArrayList<>();
    private final AtomicLong sequencia = new AtomicLong();

    public synchronized void limpar() {
        eventos.clear();
    }

    public synchronized List<EventoDeAuditoria> todos() {
        return List.copyOf(eventos);
    }

    @Override
    public synchronized EventoDeAuditoria registrar(EventoDeAuditoria evento) {
        if (evento.id() != null) {
            throw new IllegalStateException("Eventos de auditoria não são alterados depois de gravados");
        }
        var gravado = evento.comId(sequencia.incrementAndGet());
        eventos.add(gravado);
        return gravado;
    }

    @Override
    public synchronized Pagina<EventoDeAuditoria> listar(FiltroAuditoria filtro, Paginacao paginacao) {
        return Paginador.paginar(eventos.stream().filter(filtro::aceita).toList(), paginacao);
    }
}
