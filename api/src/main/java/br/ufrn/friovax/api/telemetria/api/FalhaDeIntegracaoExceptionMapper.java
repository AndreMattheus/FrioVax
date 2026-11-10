package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Falha no contrato entre a API e o serviço de análise: {@code 502}, nunca {@code 4xx}, porque o erro não é de quem
 * enviou a requisição. O detalhe da falha fica no log do adaptador e não é devolvido.
 */
@Provider
public class FalhaDeIntegracaoExceptionMapper implements ExceptionMapper<FalhaDeIntegracao> {

    static final String TIPO = "/problemas/falha-de-integracao";

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(FalhaDeIntegracao exception) {
        var problema = new ProblemDetails(TIPO, "Falha na integração com a análise térmica", 502,
                "A API não conseguiu obter uma análise térmica válida. Nenhuma leitura foi confirmada.",
                uriInfo != null ? uriInfo.getRequestUri().getRawPath() : null);
        return Response.status(502)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
