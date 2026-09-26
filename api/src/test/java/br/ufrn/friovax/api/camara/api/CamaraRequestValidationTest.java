package br.ufrn.friovax.api.camara.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

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
    void rejeitaCapacidadeNegativa() {
        CamaraRequest request = new CamaraRequest(
                "CAM-01", "Câmara fria principal", "UBS Centro",
                -10, new BigDecimal("2.0"), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL
        );

        assertFalse(validator.validate(request).isEmpty());
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
