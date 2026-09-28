package br.ufrn.friovax.api.camara.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TemperaturaValidaValidator implements ConstraintValidator<TemperaturaValida, CamaraRequest> {

    @Override
    public boolean isValid(CamaraRequest value, ConstraintValidatorContext context) {
        if (value == null || value.temperaturaMinima() == null || value.temperaturaMaxima() == null) {
            return true;
        }

        boolean valido = value.temperaturaMinima().compareTo(value.temperaturaMaxima()) < 0;

        if (!valido) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("deve ser menor que temperaturaMaxima")
                    .addPropertyNode("temperaturaMinima")
                    .addConstraintViolation();
        }

        return valido;
    }
}
