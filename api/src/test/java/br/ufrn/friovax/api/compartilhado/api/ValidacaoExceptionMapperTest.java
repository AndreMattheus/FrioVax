package br.ufrn.friovax.api.compartilhado.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ValidacaoExceptionMapperTest {

    @Test
    void retorna422ComFormatoDeProblemDetails() {
        Set<ConstraintViolation<Object>> violacoes = gerarViolacaoDeExemplo();
        ConstraintViolationException excecao = new ConstraintViolationException(violacoes);

        ValidacaoExceptionMapper mapper = new ValidacaoExceptionMapper();
        // uriInfo fica null aqui de propósito (teste unitário puro, sem @QuarkusTest)
        // o mapper já foi escrito pra não quebrar nesse caso.

        Response resposta = mapper.toResponse(excecao);

        assertEquals(422, resposta.getStatus());
        assertInstanceOf(ProblemDetails.class, resposta.getEntity());

        ProblemDetails problema = (ProblemDetails) resposta.getEntity();
        assertEquals(422, problema.status());
        assertEquals(1, problema.erros().size());
    }

    //gera uma violação real usando um objeto de exemplo
    @SuppressWarnings("unchecked")
    private Set<ConstraintViolation<Object>> gerarViolacaoDeExemplo() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ExemploComRegra exemplo = new ExemploComRegra(null);
        Set<ConstraintViolation<ExemploComRegra>> violacoes = validator.validate(exemplo);
        return (Set<ConstraintViolation<Object>>) (Set<?>) violacoes;
    }

    record ExemploComRegra(@jakarta.validation.constraints.NotNull BigDecimal campo) {}
}
