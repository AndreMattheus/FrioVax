package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontradoException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecursoNaoEncontradoExceptionMapperTest {

    @Test
    void retorna404ComTypeCorreto() {
        RecursoNaoEncontradoExceptionMapper mapper = new RecursoNaoEncontradoExceptionMapper();
        RecursoNaoEncontradoException excecao =
                new RecursoNaoEncontradoException("Câmara com id 99 não encontrada");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(404, resposta.getStatus());
        assertEquals("/problemas/recurso-nao-encontrado", problema.type());
        assertEquals("Câmara com id 99 não encontrada", problema.detail());
    }
}
