package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.dominio.ConflitoDeLeitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConflitante;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Conflitos da recepção de leitura: {@code 409} com tipos próprios, para o cliente distinguir chave reutilizada com
 * outro conteúdo (não adianta reenviar) de alteração concorrente (pode reenviar a mesma leitura).
 */
@Provider
public class ConflitoDeLeituraExceptionMapper implements ExceptionMapper<ConflitoDeLeitura> {

    static final String TIPO_CONFLITANTE = "/problemas/leitura-conflitante";
    static final String TIPO_CONCORRENTE = "/problemas/alteracao-concorrente";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConflitoDeLeitura exception) {
        boolean conflitante = exception instanceof LeituraConflitante;
        var problema = new ProblemDetails(
                conflitante ? TIPO_CONFLITANTE : TIPO_CONCORRENTE,
                conflitante ? "Leitura já registrada com outro conteúdo" : "Dados alterados durante a análise",
                409, exception.getMessage(),
                uriInfo != null ? uriInfo.getRequestUri().getRawPath() : null);
        return Response.status(409)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
