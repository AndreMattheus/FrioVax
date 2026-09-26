package br.ufrn.friovax.api.compartilhado.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

//segurança genérica para tudo que não tem mapper mais específico:
//- JSON malformado no corpo da requisição -> 400
//- qualquer outra coisa não esperada -> 500 genérico, sem stack trace no corpo (mas logado no servidor, pra investigar depois)
@Provider
public class ErroGenericoExceptionMapper implements ExceptionMapper<Exception> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
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

    private Response construir(int status, String tipo, String titulo, String detalhe) {
        ProblemDetails problema = new ProblemDetails(
                tipo, titulo, status, detalhe,
                uriInfo != null ? uriInfo.getPath() : null
        );
        return Response.status(status)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }
}
