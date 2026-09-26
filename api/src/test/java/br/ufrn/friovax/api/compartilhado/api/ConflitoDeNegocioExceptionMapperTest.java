package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocioException;
import br.ufrn.friovax.api.compartilhado.dominio.TipoConflito;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConflitoDeNegocioExceptionMapperTest {

    private final ConflitoDeNegocioExceptionMapper mapper = new ConflitoDeNegocioExceptionMapper();

    @Test
    void codigoDuplicadoUsaOTypeCorreto() {
        var excecao = new ConflitoDeNegocioException(
                TipoConflito.CODIGO_DUPLICADO, "Já existe uma câmara com o código CAM-01");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/codigo-duplicado", problema.type());
    }

    @Test
    void capacidadeExcedidaUsaOTypeCorreto() {
        var excecao = new ConflitoDeNegocioException(
                TipoConflito.CAPACIDADE_EXCEDIDA, "Capacidade da câmara excedida");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/capacidade-excedida", problema.type());
    }

    @Test
    void estadoIncompativelUsaOTypeCorreto() {
        var excecao = new ConflitoDeNegocioException(
                TipoConflito.ESTADO_INCOMPATIVEL, "Câmara em manutenção não aceita novos lotes");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(409, resposta.getStatus());
        assertEquals("/problemas/estado-incompativel", problema.type());
    }
}
