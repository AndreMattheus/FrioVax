package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.dominio.ConflitoDeResolucao;
import br.ufrn.friovax.api.telemetria.dominio.ResolucaoConflitante;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflitoDeResolucaoExceptionMapper implements ExceptionMapper<ConflitoDeResolucao> {

    static final String TIPO_NAO_PERMITIDA = "/problemas/resolucao-nao-permitida";
    static final String TIPO_CONFLITANTE = "/problemas/resolucao-conflitante";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConflitoDeResolucao exception) {
        boolean conflitante = exception instanceof ResolucaoConflitante;
        var problema = new ProblemDetails(
                conflitante ? TIPO_CONFLITANTE : TIPO_NAO_PERMITIDA,
                conflitante ? "Ocorrência já resolvida com outro conteúdo" : "Resolução ainda não permitida",
                409, exception.getMessage(),
                uriInfo != null ? uriInfo.getRequestUri().getRawPath() : null);
        return Response.status(409)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
