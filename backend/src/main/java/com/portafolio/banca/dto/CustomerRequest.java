package com.portafolio.banca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un cliente FICTICIO junto con su usuario de acceso (rol CUSTOMER). */
public record CustomerRequest(
        @Schema(example = "María López (demo)")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite como máximo 120 caracteres")
        String fullName,

        @Schema(example = "maria.lopez@banca.demo")
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Email inválido")
        @Size(max = 160, message = "El email admite como máximo 160 caracteres")
        String email,

        @Schema(example = "+52 55 0000 0003", description = "Teléfono ficticio")
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "El teléfono solo admite dígitos, espacios, paréntesis y guiones")
        String phone,

        @Schema(example = "Demo1234!", description = "Contraseña de acceso del cliente (mínimo 8 caracteres)")
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password) {
}
