package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Dá a cada requisição uma correlação: a do cabeçalho {@code X-Correlation-Id}, quando vem no formato aceito, ou uma
 * gerada pelo servidor. A correlação vai para os eventos de auditoria, para os logs ({@code %X{correlacao}}) e volta
 * no mesmo cabeçalho da resposta. Um valor fora do formato é trocado, nunca copiado para log ou banco.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class CorrelacaoFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    static final String CHAVE_MDC = "correlacao";

    @Inject
    CorrelacaoDaRequisicao correlacao;

    @Override
    public void filter(ContainerRequestContext requisicao) {
        var recebida = requisicao.getHeaderString(CABECALHO);
        var id = EventoDeAuditoria.correlacaoValida(recebida) ? recebida : UUID.randomUUID().toString();
        correlacao.definir(id);
        MDC.put(CHAVE_MDC, id);
    }

    @Override
    public void filter(ContainerRequestContext requisicao, ContainerResponseContext resposta) {
        var id = correlacao.id();
        if (id != null) {
            resposta.getHeaders().putSingle(CABECALHO, id);
        }
        MDC.remove(CHAVE_MDC);
    }
}
