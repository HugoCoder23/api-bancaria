package com.portafolio.banca.exception;

/**
 * La petición es válida en forma pero viola una regla de negocio (saldo insuficiente, cuenta bloqueada...).
 * Se responde con 422. Si el intento quedó registrado como movimiento REJECTED, incluye su referencia.
 */
public class BusinessRuleException extends RuntimeException {
    private final String code;
    private final String reference;

    public BusinessRuleException(String code, String message) {
        this(code, message, null);
    }

    public BusinessRuleException(String code, String message, String reference) {
        super(message);
        this.code = code;
        this.reference = reference;
    }

    public String getCode() { return code; }
    public String getReference() { return reference; }
}
