package br.ufrn.friovax.api.compartilhado.api;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErroGenericoExceptionMapperTest {

    @Test
    void jsonMalformadoRetorna400() {
        ErroGenericoExceptionMapper mapper = new ErroGenericoExceptionMapper();
        JsonParseException causaJson = new JsonParseException(null, "json quebrado de propósito");
        Exception excecaoEnvolvida = new RuntimeException("erro ao ler corpo", causaJson);

        Response resposta = mapper.toResponse(excecaoEnvolvida);

        assertEquals(400, resposta.getStatus());
    }

    @Test
    void erroInesperadoRetorna500SemVazarDetalheInterno() {
        ErroGenericoExceptionMapper mapper = new ErroGenericoExceptionMapper();
        Exception excecao = new IllegalStateException("detalhe interno sensível que não pode aparecer pro cliente");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(500, resposta.getStatus());
        assertFalse(problema.detail().contains("detalhe interno sensível"));
    }
}
