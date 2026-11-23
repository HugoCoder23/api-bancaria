package com.portafolio.banca.exception;

/**
 * Recurso inexistente. También se usa cuando el recurso existe pero pertenece a otro cliente:
 * así la API no revela qué identificadores o referencias existen (evita la enumeración).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
