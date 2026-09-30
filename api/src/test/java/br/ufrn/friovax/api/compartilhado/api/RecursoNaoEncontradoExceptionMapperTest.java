package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecursoNaoEncontradoExceptionMapperTest {

    @Test
    void retorna404ComTypeCorreto() {
        RecursoNaoEncontradoExceptionMapper mapper = new RecursoNaoEncontradoExceptionMapper();
        RecursoNaoEncontrado excecao = new RecursoNaoEncontrado("Câmara", 99);

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(404, resposta.getStatus());
        assertEquals("/problemas/recurso-nao-encontrado", problema.type());
        assertEquals("Câmara 99 não encontrado(a).", problema.detail());
    }

    @Test
    void incluiCaminhoAbsolutoDaRotaNoProblema() {
        var mapper = new RecursoNaoEncontradoExceptionMapper();
        mapper.uriInfo = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class }, (proxy, metodo, args) ->
                        metodo.getName().equals("getRequestUri")
                                ? URI.create("http://localhost:8080/api/camaras/99") : null);

        var resposta = mapper.toResponse(new RecursoNaoEncontrado("Câmara", 99));
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals("/api/camaras/99", problema.instance());
        assertEquals("application/problem+json", resposta.getMediaType().toString());
    }
}
