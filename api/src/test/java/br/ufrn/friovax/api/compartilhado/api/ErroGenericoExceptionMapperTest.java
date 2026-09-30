package br.ufrn.friovax.api.compartilhado.api;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErroGenericoExceptionMapperTest {

    @Test
    void jsonMalformadoRetorna400() {
        ErroGenericoExceptionMapper mapper = new ErroGenericoExceptionMapper();
        JsonParseException causaJson = new JsonParseException(null, "json quebrado de propósito");
        Exception excecaoEnvolvida = new RuntimeException("erro ao ler body", causaJson);

        Response resposta = mapper.toResponse(excecaoEnvolvida);

        assertEquals(400, resposta.getStatus());
        assertEquals("application/problem+json", resposta.getMediaType().toString());
        assertEquals("/problemas/requisicao-invalida", ((ProblemDetails) resposta.getEntity()).type());
    }

    @Test
    void jsonMalformadoDentroDeBadRequestMantemContratoSemExporDetalhes() {
        var mapper = new ErroGenericoExceptionMapper();
        var causa = new JsonParseException(null, "detalhe técnico do parser");
        var excecao = new BadRequestException("falha de leitura", causa);

        var resposta = mapper.toResponse(excecao);
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals(400, resposta.getStatus());
        assertEquals(400, problema.status());
        assertEquals("/problemas/requisicao-invalida", problema.type());
        assertEquals("Requisição inválida", problema.title());
        assertEquals("O body da requisição não é um JSON válido.", problema.detail());
    }

    @Test
    void badRequestSemCausaJsonUsaTipoEDetalheDoContrato() {
        var resposta = new ErroGenericoExceptionMapper().toResponse(new BadRequestException("detalhe técnico"));
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals(400, resposta.getStatus());
        assertEquals("/problemas/requisicao-invalida", problema.type());
        assertEquals("Requisição inválida", problema.title());
        assertEquals("A requisição contém dados inválidos.", problema.detail());
    }

    @Test
    void metodoNaoPermitidoPreservaStatus405() {
        var resposta = new ErroGenericoExceptionMapper().toResponse(new NotAllowedException("POST"));
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals(405, resposta.getStatus());
        assertEquals("/problemas/metodo-nao-permitido", problema.type());
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

    @Test
    void webApplicationExceptionPreservaOStatusOriginal() {
        ErroGenericoExceptionMapper mapper = new ErroGenericoExceptionMapper();
        NotFoundException excecao = new NotFoundException("rota não existe");

        Response resposta = mapper.toResponse(excecao);

        assertEquals(404, resposta.getStatus());
    }
}
