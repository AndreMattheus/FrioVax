package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

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
}
