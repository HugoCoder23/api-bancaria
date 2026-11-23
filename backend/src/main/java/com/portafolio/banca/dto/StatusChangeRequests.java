package com.portafolio.banca.dto;

import com.portafolio.banca.entity.AccountStatus;
import com.portafolio.banca.entity.CustomerStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Peticiones de cambio de estado (solo operadores y administradores). */
public final class StatusChangeRequests {

    private StatusChangeRequests() {}

    public record CustomerStatusRequest(
            @NotNull(message = "El estado es obligatorio") CustomerStatus status,
            @Size(max = 120, message = "El motivo admite como máximo 120 caracteres") String reason) {
    }

    public record AccountStatusRequest(
            @NotNull(message = "El estado es obligatorio") AccountStatus status,
            @Size(max = 120, message = "El motivo admite como máximo 120 caracteres") String reason) {
    }
}
