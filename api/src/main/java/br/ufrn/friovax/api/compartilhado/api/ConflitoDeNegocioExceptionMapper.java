package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocioException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflitoDeNegocioExceptionMapper implements ExceptionMapper<ConflitoDeNegocioException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConflitoDeNegocioException exception) {
        ProblemDetails problema = new ProblemDetails(
                exception.tipo().tipoUri(),
                exception.tipo().tituloPadrao(),
                409,
                exception.getMessage(),
                uriInfo != null ? uriInfo.getPath() : null
        );

        return Response.status(409)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
