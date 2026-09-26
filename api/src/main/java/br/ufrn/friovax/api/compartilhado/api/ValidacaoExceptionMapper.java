package br.ufrn.friovax.api.compartilhado.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

//Pega violações de Bean Validation (@Valid nos parâmetros do endpoint) e converte para o formato Problem Details.

@Provider
public class ValidacaoExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ProblemDetails.CampoErro> erros = exception.getConstraintViolations().stream()
                .map(this::paraCampoErro)
                .toList();

        ProblemDetails problema = new ProblemDetails(
                "/problemas/validacao",
                "Dados inválidos",
                422,
                "A requisição contém " + erros.size() + " campo(s) inválido(s).",
                caminhoAtual(),
                erros
        );

        return Response.status(422)
                .type("application/problem+json")
                .entity(problema)
                .build();
    }

    private ProblemDetails.CampoErro paraCampoErro(ConstraintViolation<?> violacao) {
        String caminho = violacao.getPropertyPath().toString();
        String campo = caminho.contains(".") ? caminho.substring(caminho.lastIndexOf('.') + 1) : caminho;
        return new ProblemDetails.CampoErro(campo, violacao.getMessage());
    }

    private String caminhoAtual() {
        // uriInfo pode vir nulo em teste unitário puro (sem subir o Quarkus)
        return uriInfo != null ? uriInfo.getPath() : null;
    }
}
