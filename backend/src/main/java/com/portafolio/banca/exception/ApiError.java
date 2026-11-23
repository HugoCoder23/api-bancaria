package com.portafolio.banca.exception;

import java.time.Instant;
import java.util.List;

/**
 * Formato único para todas las respuestas de error de la API.
 *
 * @param reference referencia del movimiento rechazado, cuando el rechazo quedó registrado en el historial
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String reference,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}
}
