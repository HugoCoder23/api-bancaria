package com.portafolio.banca.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bloqueo optimista como segunda barrera; las operaciones de dinero usan además bloqueo pesimista. */
    @Version
    private long version;

    protected Account() {}

    public Account(Customer customer, String number, AccountType type, Currency currency) {
        this.customer = customer;
        this.number = number;
        this.type = type;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() { openedAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    /** El saldo solo cambia mediante estos dos métodos, llamados desde LedgerService dentro de una transacción. */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        BigDecimal result = balance.subtract(amount);
        if (result.signum() < 0) {
            // Las reglas de negocio lo validan antes; esto protege el invariante si alguien las omite.
            throw new IllegalStateException("El saldo no puede quedar negativo");
        }
        balance = result;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser mayor que cero");
        }
    }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }
    public boolean isOwnedBy(Long customerId) { return customerId != null && customerId.equals(customer.getId()); }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getNumber() { return number; }
    public AccountType getType() { return type; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getOpenedAt() { return openedAt; }
}
