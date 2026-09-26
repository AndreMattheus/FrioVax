package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontradoException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RecursoNaoEncontradoExceptionMapper implements ExceptionMapper<RecursoNaoEncontradoException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(RecursoNaoEncontradoException exception) {
        ProblemDetails problema = new ProblemDetails(
                "/problemas/recurso-nao-encontrado",
                "Recurso não encontrado",
                404,
                exception.getMessage(),
                uriInfo != null ? uriInfo.getPath() : null
        );

        return Response.status(404)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
