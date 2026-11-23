package com.portafolio.banca.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.*;

/**
 * Regla única para importes de dinero: obligatorio, estrictamente positivo, como máximo 2 decimales
 * y un límite por operación de 1,000,000.00 (ficticio). Se aplica a BigDecimal: nunca float/double.
 */
@NotNull(message = "El importe es obligatorio")
@DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
@DecimalMax(value = "1000000.00", message = "El importe máximo por operación es 1,000,000.00")
@Digits(integer = 7, fraction = 2, message = "El importe admite como máximo 2 decimales")
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidAmount {
    String message() default "Importe inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
