package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamaraRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aceitaRequestValido() {
        CamaraRequest request = new CamaraRequest(
            "CAM-01", "Câmara fria principal", "UBS Centro",
            5000, new BigDecimal("2.0"), new BigDecimal("8.0"),
            EstadoCamara.OPERACIONAL
        );

        Set<ConstraintViolation<CamaraRequest>> violacoes = validator.validate(request);

        assertTrue(violacoes.isEmpty(), "não esperava violações: " + violacoes);
    }

    @Test
    void permiteQueODominioValideOTamanhoDepoisDoTrim() {
        var texto = " " + "x".repeat(100) + " ";
        var request = new CamaraRequest("CAM-01", texto, texto, 10,
                BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void camposAusentesTemMensagensEmPortugues() {
        var request = new CamaraRequest(null, null, null, null, null, null, null);
        var mensagens = validator.validate(request).stream().collect(Collectors.toMap(
                violacao -> violacao.getPropertyPath().toString(), ConstraintViolation::getMessage));

        assertEquals(Map.of(
                "codigo", "deve ser informado",
                "nome", "deve ser informado",
                "unidade", "deve ser informada",
                "capacidade", "deve ser informada",
                "temperaturaMinima", "deve ser informada",
                "temperaturaMaxima", "deve ser informada",
                "estado", "deve ser informado"
        ), mensagens);
    }

    @Test
    void rejeitaCapacidadeNegativa() {
        CamaraRequest request = new CamaraRequest(
            "CAM-01", "Câmara fria principal", "UBS Centro",
            -10, new BigDecimal("2.0"), new BigDecimal("8.0"),
            EstadoCamara.OPERACIONAL
        );

        var violacoes = validator.validate(request);
        assertEquals(1, violacoes.size());
        assertEquals("deve ser maior que zero", violacoes.iterator().next().getMessage());
    }

    @Test
    void rejeitaTemperaturaMinimaMaiorOuIgualQueMaxima() {
        CamaraRequest request = new CamaraRequest(
            "CAM-01", "Câmara fria principal", "UBS Centro",
            5000, new BigDecimal("10.0"), new BigDecimal("2.0"),
            EstadoCamara.OPERACIONAL
        );

        Set<ConstraintViolation<CamaraRequest>> violacoes = validator.validate(request);

        assertFalse(violacoes.isEmpty());
    }

    @Test
    void rejeitaCodigoEmBranco() {
        CamaraRequest request = new CamaraRequest(
            "  ", "Câmara fria principal", "UBS Centro",
            5000, new BigDecimal("2.0"), new BigDecimal("8.0"),
            EstadoCamara.OPERACIONAL
        );

        assertFalse(validator.validate(request).isEmpty());
    }
}
