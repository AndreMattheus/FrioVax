package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.dominio.AutenticacaoInvalida;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AutenticacaoInvalidaExceptionMapper implements ExceptionMapper<AutenticacaoInvalida> {
    @Context UriInfo uriInfo;

    @Override
    public Response toResponse(AutenticacaoInvalida erro) {
        return Response.status(401).type("application/problem+json")
                .header("WWW-Authenticate", "Bearer")
                .header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .entity(new ProblemDetails("/problemas/autenticacao-invalida", "Autenticação inválida", 401,
                        erro.getMessage(), uriInfo.getRequestUri().getRawPath())).build();
    }
}
