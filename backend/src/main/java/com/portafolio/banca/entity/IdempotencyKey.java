package com.portafolio.banca.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Registro de una operación sensible ejecutada con una Idempotency-Key. Se guarda en la MISMA transacción
 * de base de datos que el movimiento: si dos peticiones con la misma clave compiten, la restricción única
 * (user_id, idem_key) hace fallar a la segunda y su movimiento se revierte por completo.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String key;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 de la operación y sus parámetros: detecta la reutilización de una clave con otra petición. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    /** La respuesta se reconstruye a partir del movimiento inmutable con esta referencia. */
    @Column(name = "transaction_reference", nullable = false, length = 30)
    private String transactionReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, Long userId, String requestHash, int responseStatus,
                          String transactionReference, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.userId = userId;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.transactionReference = transactionReference;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public Long getUserId() { return userId; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getTransactionReference() { return transactionReference; }
    public Instant getExpiresAt() { return expiresAt; }
}
