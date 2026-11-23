package com.portafolio.banca.exception;

/** El recurso entra en conflicto con el estado actual (p. ej. email duplicado). */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
