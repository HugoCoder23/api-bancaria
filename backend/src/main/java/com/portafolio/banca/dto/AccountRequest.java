package com.portafolio.banca.dto;

import com.portafolio.banca.entity.AccountType;
import com.portafolio.banca.entity.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AccountRequest(
        @Schema(description = "Obligatorio para operadores. Un cliente solo puede abrir cuentas a su nombre y puede omitirlo.")
        Long customerId,
        @NotNull(message = "El tipo de cuenta es obligatorio") AccountType type,
        @NotNull(message = "La moneda es obligatoria") Currency currency) {
}
