package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ValidacaoDeNegocioExceptionMapper implements ExceptionMapper<ValidacaoDeNegocio> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ValidacaoDeNegocio exception) {
        ProblemDetails problema = new ProblemDetails(
            "/problemas/validacao",
            "Dados inválidos",
            422,
            exception.getMessage(),
            uriInfo != null ? uriInfo.getPath() : null,
            List.of(new ProblemDetails.CampoErro(exception.campo(), exception.getMessage()))
        );

        return Response.status(422)
            .type("application/problem+json")
            .entity(problema)
            .build();
    }
}
