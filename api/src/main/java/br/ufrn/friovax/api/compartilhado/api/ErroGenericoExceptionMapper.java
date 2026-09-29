package br.ufrn.friovax.api.compartilhado.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ErroGenericoExceptionMapper implements ExceptionMapper<Exception> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            if (status == 400) {
                String detalhe = ehJsonMalformado(exception)
                    ? "O corpo da requisição não é um JSON válido."
                    : "A requisição contém dados inválidos.";
                return construir(status, "/problemas/requisicao-invalida", "Requisição inválida", detalhe);
            }
            return construir(status, tipoParaStatus(status), tituloParaStatus(status), exception.getMessage());
        }

        if (ehJsonMalformado(exception)) {
            return construir(400, "/problemas/requisicao-invalida", "Requisição inválida",
                "O corpo da requisição não é um JSON válido.");
        }

        Log.error("Erro não tratado na API", exception);
        return construir(500, "/problemas/erro-interno", "Erro interno",
            "Ocorreu um erro inesperado. Tente novamente ou contate o suporte.");
    }

    private boolean ehJsonMalformado(Throwable t) {
        for (Throwable atual = t; atual != null; atual = atual.getCause()) {
            if (atual instanceof JsonProcessingException) {
                return true;
            }
        }
        return false;
    }

    private String tipoParaStatus(int status) {
        return switch (status) {
            case 404 -> "/problemas/recurso-nao-encontrado";
            case 405 -> "/problemas/metodo-nao-permitido";
            default -> "/problemas/erro";
        };
    }

    private String tituloParaStatus(int status) {
        return switch (status) {
            case 404 -> "Recurso não encontrado";
            case 405 -> "Método não permitido";
            default -> "Erro na requisição";
        };
    }

    private Response construir(int status, String tipo, String titulo, String detalhe) {
        ProblemDetails problema = new ProblemDetails(
            tipo, titulo, status, detalhe,
            uriInfo != null ? uriInfo.getRequestUri().getRawPath() : null
        );
        return Response.status(status)
            .type("application/problem+json")
            .entity(problema)
            .build();
    }
}
