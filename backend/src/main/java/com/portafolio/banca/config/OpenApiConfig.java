package com.portafolio.banca.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Banking API",
                version = "1.0.0",
                description = """
                        API REST que **simula** operaciones bancarias: clientes, cuentas, depósitos, retiros y \
                        transferencias. Proyecto de portafolio educativo: **todos los datos y el dinero son ficticios**.

                        **Cómo probarla**
                        1. `POST /api/auth/login` con una cuenta demo (p. ej. `ana.garcia@banca.demo` / `Demo1234!`).
                        2. Copia el `token` y pulsa **Authorize**.
                        3. Las operaciones que mueven dinero exigen la cabecera `Idempotency-Key` (p. ej. un UUID). \
                        Repetir la petición con la misma clave devuelve el resultado original sin duplicar el movimiento.
                        """),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
