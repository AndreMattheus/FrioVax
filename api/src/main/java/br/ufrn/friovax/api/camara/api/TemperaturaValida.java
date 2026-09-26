package br.ufrn.friovax.api.camara.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TemperaturaValidaValidator.class)
public @interface TemperaturaValida {
    String message() default "temperaturaMinima deve ser menor que temperaturaMaxima";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
