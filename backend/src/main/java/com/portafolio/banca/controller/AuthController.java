package com.portafolio.banca.controller;

import com.portafolio.banca.dto.LoginRequest;
import com.portafolio.banca.dto.LoginResponse;
import com.portafolio.banca.dto.UserResponse;
import com.portafolio.banca.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // público
    @Operation(summary = "Autenticar usuario y obtener un JWT",
            description = "Cuentas demo (contraseña Demo1234!): ana.garcia@banca.demo, luis.perez@banca.demo "
                    + "(clientes), operador@banca.demo, admin@banca.demo.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    public UserResponse me() {
        return authService.me();
    }
}
