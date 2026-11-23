package com.portafolio.banca.dto;

import com.portafolio.banca.validation.ValidAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @Schema(example = "1")
        @NotNull(message = "La cuenta origen es obligatoria")
        Long sourceAccountId,

        @Schema(description = "Número de la cuenta destino (12 dígitos; se ignoran espacios y guiones)",
                example = "990000000004")
        @NotBlank(message = "La cuenta destino es obligatoria")
        @Pattern(regexp = "^[0-9 -]{12,20}$", message = "El número de cuenta destino no es válido")
        String destinationAccountNumber,

        @Schema(example = "250.00") @ValidAmount BigDecimal amount,

        @Schema(example = "Pago de renta (ficticio)")
        @Size(max = 140, message = "La descripción admite como máximo 140 caracteres")
        String description) {
}
