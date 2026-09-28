package br.ufrn.friovax.api.compartilhado.api;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MismatchedInputExceptionMapper implements ExceptionMapper<MismatchedInputException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(MismatchedInputException exception) {
        String campo = exception.getPath().isEmpty()
                ? null
                : exception.getPath().get(exception.getPath().size() - 1).getFieldName();

        String detalhe = campo != null
                ? "O campo '" + campo + "' contém um valor incompatível com o tipo esperado."
                : "O corpo da requisição contém um valor incompatível com o tipo esperado.";

        ProblemDetails problema = new ProblemDetails(
                "/problemas/requisicao-invalida",
                "Requisição inválida",
                400,
                detalhe,
                uriInfo != null ? uriInfo.getPath() : null
        );

        return Response.status(400)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
