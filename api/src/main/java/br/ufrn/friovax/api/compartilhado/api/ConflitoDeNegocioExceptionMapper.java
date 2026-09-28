package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocio;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflitoDeNegocioExceptionMapper implements ExceptionMapper<ConflitoDeNegocio> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConflitoDeNegocio exception) {
        String tipoUri;
        String titulo;

        if (exception instanceof CodigoDuplicado) {
            tipoUri = "/problemas/codigo-duplicado";
            titulo = "Código duplicado";
        } else if (exception instanceof CapacidadeExcedida) {
            tipoUri = "/problemas/capacidade-excedida";
            titulo = "Capacidade da câmara excedida";
        } else if (exception instanceof EstadoIncompativel) {
            tipoUri = "/problemas/estado-incompativel";
            titulo = "Operação incompatível com o estado atual";
        } else {
            tipoUri = "/problemas/conflito";
            titulo = "Conflito de negócio";
        }

        ProblemDetails problema = new ProblemDetails(
            tipoUri, titulo, 409, exception.getMessage(),
            uriInfo != null ? uriInfo.getPath() : null
        );

        return Response.status(409)
            .type("application/problem+json")
            .entity(problema)
            .build();
    }
}
