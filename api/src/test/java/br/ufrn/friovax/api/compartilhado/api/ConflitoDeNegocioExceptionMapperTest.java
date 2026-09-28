package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConflitoDeNegocioExceptionMapperTest {

    private final ConflitoDeNegocioExceptionMapper mapper = new ConflitoDeNegocioExceptionMapper();

    @Test
    void codigoDuplicadoUsaOTypeCorreto() {
        var excecao = new CodigoDuplicado("uma câmara", "CAM-01");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/codigo-duplicado", problema.type());
    }

    @Test
    void capacidadeExcedidaUsaOTypeCorreto() {
        var excecao = new CapacidadeExcedida("Capacidade da câmara excedida");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/capacidade-excedida", problema.type());
    }

    @Test
    void estadoIncompativelUsaOTypeCorreto() {
        var excecao = new EstadoIncompativel("Câmara em manutenção não aceita novos lotes");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/estado-incompativel", problema.type());
    }
}
