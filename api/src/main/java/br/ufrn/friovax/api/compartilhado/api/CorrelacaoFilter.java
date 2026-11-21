package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Dá a cada requisição uma correlação: a do cabeçalho {@code X-Correlation-Id}, quando vem no formato aceito, ou uma
 * gerada pelo servidor. A correlação vai para os eventos de auditoria, para os logs ({@code %X{correlacao}}) e volta
 * no mesmo cabeçalho da resposta. Um valor fora do formato é trocado, nunca copiado para log ou banco.
 */
@ApplicationScoped
public class CorrelacaoFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    static final String CHAVE_MDC = "correlacao";

    // Filtros HTTP com prioridade maior executam primeiro; a autenticação do Quarkus usa 200.
    private static final int PRIORIDADE_ANTES_DA_AUTENTICACAO = 1000;

    void registrar(@Observes Filters filtros) {
        filtros.register(this::filtrar, PRIORIDADE_ANTES_DA_AUTENTICACAO);
    }

    private void filtrar(RoutingContext requisicao) {
        var recebida = requisicao.request().getHeader(CABECALHO);
        var id = EventoDeAuditoria.correlacaoValida(recebida) ? recebida : UUID.randomUUID().toString();
        requisicao.put(CABECALHO, id);
        MDC.put(CHAVE_MDC, id);
        // Executa também quando segurança, healthcheck ou ausência de rota encerram a resposta.
        requisicao.addHeadersEndHandler(ignorado -> requisicao.response().putHeader(CABECALHO, id));
        requisicao.addEndHandler(ignorado -> MDC.remove(CHAVE_MDC));
        requisicao.next();
    }
}
