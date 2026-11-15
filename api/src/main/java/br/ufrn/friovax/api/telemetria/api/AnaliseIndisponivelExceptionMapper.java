package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Indisponibilidade controlada do serviço de análise: {@code 503}, sem confirmar leituras (ADR 0003, seção 7). */
@Provider
public class AnaliseIndisponivelExceptionMapper implements ExceptionMapper<AnaliseIndisponivel> {

    static final String TIPO = "/problemas/analise-indisponivel";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(AnaliseIndisponivel exception) {
        var problema = new ProblemDetails(TIPO, "Análise térmica indisponível", 503,
                exception.getMessage() + " Nenhuma leitura foi confirmada; tente novamente.",
                uriInfo != null ? uriInfo.getRequestUri().getRawPath() : null);
        return Response.status(503)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
