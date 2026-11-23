package com.portafolio.banca.dto;

import com.portafolio.banca.validation.ValidAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Depósito o retiro simulado. */
public record MoneyOperationRequest(
        @Schema(example = "1500.00") @ValidAmount BigDecimal amount,
        @Schema(example = "Operación de prueba")
        @Size(max = 140, message = "La descripción admite como máximo 140 caracteres")
        String description) {
}
