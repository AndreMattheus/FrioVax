package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidacaoDeNegocioExceptionMapperTest {

    @Test
    void retorna422ComOCampoInvalidoNaLista() {
        ValidacaoDeNegocioExceptionMapper mapper = new ValidacaoDeNegocioExceptionMapper();
        var excecao = new ValidacaoDeNegocio("capacidade", "deve ser maior que zero");

        Response resposta = mapper.toResponse(excecao);
        ProblemDetails problema = (ProblemDetails) resposta.getEntity();

        assertEquals(422, resposta.getStatus());
        assertEquals("/problemas/validacao", problema.type());
        assertEquals(1, problema.erros().size());
        assertEquals("capacidade", problema.erros().get(0).campo());
    }
}
