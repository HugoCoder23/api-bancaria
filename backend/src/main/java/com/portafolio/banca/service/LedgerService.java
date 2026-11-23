package com.portafolio.banca.service;

import com.portafolio.banca.entity.*;
import com.portafolio.banca.exception.ResourceNotFoundException;
import com.portafolio.banca.repository.AccountRepository;
import com.portafolio.banca.repository.TransactionRepository;
import com.portafolio.banca.security.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import static com.portafolio.banca.mapper.BankMapper.mask;

/**
 * Núcleo contable: el ÚNICO lugar donde cambian los saldos.
 *
 * <p>Cada método se ejecuta en una sola transacción de base de datos que incluye: bloqueo de las cuentas,
 * validación de reglas, débito/crédito, inserción del movimiento, evento de auditoría y registro de la
 * Idempotency-Key. Si cualquier paso falla, se revierte todo.</p>
 *
 * <p>Concurrencia: las cuentas se leen con {@code SELECT ... FOR UPDATE}. Dos operaciones sobre la misma
 * cuenta se ejecutan en serie y la segunda ve el saldo ya actualizado, por lo que no se puede gastar dos
 * veces el mismo saldo. En las transferencias las cuentas se bloquean siempre en orden de id ascendente
 * para evitar interbloqueos entre transferencias cruzadas (A→B y B→A).</p>
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final IdentifierGenerator ids;
    private final Clock clock;

    public LedgerService(AccountRepository accounts, TransactionRepository transactions, AccessPolicy policy,
                         AuditService audit, IdempotencyService idempotency, IdentifierGenerator ids, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.policy = policy;
        this.audit = audit;
        this.idempotency = idempotency;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public Transaction deposit(User actor, Long accountId, BigDecimal amount, String description,
                               IdempotencyService.Request idem) {
        Account account = lock(accountId);
        policy.requireMoneyMover(actor, account);
        Rejector reject = new Rejector(TransactionType.DEPOSIT, amount, null, account.getId(), description);

        requireOperable(account, reject, "La cuenta");
        account.credit(amount);
        accounts.saveAndFlush(account);

        return complete(actor, TransactionType.DEPOSIT, amount, null, account, description, idem);
    }

    @Transactional
    public Transaction withdraw(User actor, Long accountId, BigDecimal amount, String description,
                                IdempotencyService.Request idem) {
        Account account = lock(accountId);
        policy.requireMoneyMover(actor, account);
        Rejector reject = new Rejector(TransactionType.WITHDRAWAL, amount, account.getId(), null, description);

        requireOperable(account, reject, "La cuenta");
        requireFunds(account, amount, reject);
        account.debit(amount);
        accounts.saveAndFlush(account);

        return complete(actor, TransactionType.WITHDRAWAL, amount, account, null, description, idem);
    }

    @Transactional
    public Transaction transfer(User actor, Long sourceId, String destinationNumber, BigDecimal amount,
                                String description, IdempotencyService.Request idem) {
        Long destinationId = accounts.findIdByNumber(destinationNumber)
                .orElseThrow(() -> new ResourceNotFoundException("La cuenta destino no existe"));

        // Bloqueo en orden determinista (id ascendente) para evitar interbloqueos.
        Account first = lock(Math.min(sourceId, destinationId));
        Account second = sourceId.equals(destinationId) ? first : lock(Math.max(sourceId, destinationId));
        Account source = first.getId().equals(sourceId) ? first : second;
        Account destination = first.getId().equals(destinationId) ? first : second;

        policy.requireMoneyMover(actor, source);
        Rejector reject = new Rejector(TransactionType.TRANSFER, amount, source.getId(), destination.getId(), description);

        if (source.getId().equals(destination.getId())) {
            throw reject.because("SAME_ACCOUNT", "La cuenta origen y la cuenta destino deben ser distintas");
        }
        requireOperable(source, reject, "La cuenta origen");
        if (!destination.isActive() || !destination.getCustomer().isActive()) {
            throw reject.because("DESTINATION_NOT_ACTIVE", "La cuenta destino no puede recibir fondos (no está activa)");
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw reject.because("CURRENCY_MISMATCH", "Las cuentas deben tener la misma moneda (origen "
                    + source.getCurrency() + ", destino " + destination.getCurrency() + "). No hay conversión de divisas");
        }
        requireFunds(source, amount, reject);

        source.debit(amount);
        destination.credit(amount);
        // Se escriben ya los saldos (y se verifica @Version) antes de registrar el movimiento.
        accounts.saveAllAndFlush(List.of(source, destination));

        return complete(actor, TransactionType.TRANSFER, amount, source, destination, description, idem);
    }

    // ---------- helpers ----------

    private Account lock(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(AccessPolicy.ACCOUNT_NOT_FOUND));
    }

    private void requireOperable(Account account, Rejector reject, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw reject.because("ACCOUNT_NOT_ACTIVE", label + " está "
                    + (account.getStatus() == AccountStatus.BLOCKED ? "bloqueada" : "cerrada") + " y no admite operaciones");
        }
        if (!account.getCustomer().isActive()) {
            throw reject.because("CUSTOMER_BLOCKED", "El cliente titular está bloqueado y no puede operar");
        }
    }

    private void requireFunds(Account account, BigDecimal amount, Rejector reject) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw reject.because("INSUFFICIENT_FUNDS", "Saldo insuficiente: disponible "
                    + account.getBalance().toPlainString() + " " + account.getCurrency());
        }
    }

    private Transaction complete(User actor, TransactionType type, BigDecimal amount, Account source,
                                 Account destination, String description, IdempotencyService.Request idem) {
        Transaction tx = transactions.save(Transaction.completed(ids.newReference(), type, amount, source,
                destination, description, actor, clock.instant()));
        audit.record(actor, type.name(), "transaction:" + tx.getReference(), AuditResult.SUCCESS,
                amount.toPlainString() + " " + tx.getCurrency()
                        + (source != null ? " de " + mask(source.getNumber()) : "")
                        + (destination != null ? " a " + mask(destination.getNumber()) : ""));
        idempotency.save(actor, idem, HttpStatus.CREATED.value(), tx.getReference());
        // Sin datos sensibles: cuentas enmascaradas, sin nombres ni tokens.
        log.info("{} {} completada: {} {} [{} -> {}]", type, tx.getReference(), amount.toPlainString(),
                tx.getCurrency(), source == null ? "-" : mask(source.getNumber()),
                destination == null ? "-" : mask(destination.getNumber()));
        return tx;
    }

    /** Construye rechazos con el contexto de la operación en curso. */
    private record Rejector(TransactionType type, BigDecimal amount, Long sourceId, Long destinationId,
                            String description) {
        OperationRejectedException because(String code, String message) {
            return new OperationRejectedException(code, message, type, amount, sourceId, destinationId, description);
        }
    }
}
