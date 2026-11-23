package com.portafolio.banca.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Movimiento del historial. Es inmutable: Hibernate nunca emite UPDATE sobre esta entidad (@Immutable)
 * y no tiene setters. Una corrección sería un movimiento nuevo, nunca la edición de uno existente.
 */
@Entity
@Immutable
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    @Column(name = "source_balance_after", precision = 19, scale = 2)
    private BigDecimal sourceBalanceAfter;

    @Column(name = "destination_balance_after", precision = 19, scale = 2)
    private BigDecimal destinationBalanceAfter;

    @Column(length = 140)
    private String description;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transaction() {}

    private Transaction(String reference, TransactionType type, TransactionStatus status, BigDecimal amount,
                        Account source, Account destination, String description, User initiatedBy, Instant at) {
        this.reference = reference;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.currency = (source != null ? source : destination).getCurrency();
        this.sourceAccount = source;
        this.destinationAccount = destination;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.createdAt = at;
    }

    /** Movimiento aplicado. Guarda el saldo resultante de cada cuenta involucrada. */
    public static Transaction completed(String reference, TransactionType type, BigDecimal amount, Account source,
                                        Account destination, String description, User actor, Instant at) {
        Transaction t = new Transaction(reference, type, TransactionStatus.COMPLETED, amount, source, destination,
                description, actor, at);
        t.sourceBalanceAfter = source == null ? null : source.getBalance();
        t.destinationBalanceAfter = destination == null ? null : destination.getBalance();
        return t;
    }

    /** Intento rechazado por una regla de negocio. No modifica saldos. */
    public static Transaction rejected(String reference, TransactionType type, BigDecimal amount, Account source,
                                       Account destination, String description, User actor, Instant at,
                                       String failureCode, String failureReason) {
        Transaction t = new Transaction(reference, type, TransactionStatus.REJECTED, amount, source, destination,
                description, actor, at);
        t.failureCode = failureCode;
        t.failureReason = failureReason;
        return t;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public Currency getCurrency() { return currency; }
    public Account getSourceAccount() { return sourceAccount; }
    public Account getDestinationAccount() { return destinationAccount; }
    public BigDecimal getSourceBalanceAfter() { return sourceBalanceAfter; }
    public BigDecimal getDestinationBalanceAfter() { return destinationBalanceAfter; }
    public String getDescription() { return description; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public User getInitiatedBy() { return initiatedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
