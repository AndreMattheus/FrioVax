package br.ufrn.friovax.api.auditoria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

/**
 * Porta da trilha de auditoria. Só grava e consulta: não existe operação para alterar ou apagar eventos, e o papel
 * de banco da API só tem SELECT e INSERT na tabela ({@code docs/scripts/runtime-grants.sql}).
 */
public interface AuditoriaRepository {

    EventoDeAuditoria registrar(EventoDeAuditoria evento);

    Pagina<EventoDeAuditoria> listar(FiltroAuditoria filtro, Paginacao paginacao);
}
