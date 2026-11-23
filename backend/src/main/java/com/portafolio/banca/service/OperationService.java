package com.portafolio.banca.service;

import com.portafolio.banca.dto.MoneyOperationRequest;
import com.portafolio.banca.dto.TransactionResponse;
import com.portafolio.banca.dto.TransferRequest;
import com.portafolio.banca.entity.IdempotencyKey;
import com.portafolio.banca.entity.Transaction;
import com.portafolio.banca.entity.TransactionStatus;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.exception.BusinessRuleException;
import com.portafolio.banca.exception.ConflictException;
import com.portafolio.banca.mapper.BankMapper;
import com.portafolio.banca.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fachada de las operaciones de dinero: aplica la idempotencia alrededor de LedgerService.
 *
 * <ol>
 *   <li>Si la Idempotency-Key ya se usó con la misma petición, devuelve el resultado original (éxito o rechazo)
 *       sin volver a ejecutar nada.</li>
 *   <li>Si se usó con una petición distinta, responde 409.</li>
 *   <li>Si es nueva, ejecuta la operación. La clave se guarda en la misma transacción que el movimiento.</li>
 *   <li>Si dos peticiones con la misma clave llegan a la vez, la segunda choca con la restricción única,
 *       su transacción se revierte entera y se le devuelve el resultado de la primera.</li>
 * </ol>
 *
 * No es transaccional a propósito: cada paso abre su propia transacción.
 */
@Service
public class OperationService {

    private final LedgerService ledger;
    private final RejectionRecorder rejections;
    private final IdempotencyService idempotency;
    private final TransactionRepository transactions;
    private final BankMapper mapper;

    public OperationService(LedgerService ledger, RejectionRecorder rejections, IdempotencyService idempotency,
                            TransactionRepository transactions, BankMapper mapper) {
        this.ledger = ledger;
        this.rejections = rejections;
        this.idempotency = idempotency;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    /** @param replayed true si la respuesta proviene de una ejecución anterior con la misma Idempotency-Key */
    public record Result(TransactionResponse transaction, boolean replayed) {}

    public Result deposit(User actor, Long accountId, MoneyOperationRequest req, String key) {
        BigDecimal amount = normalize(req.amount());
        String description = clean(req.description());
        var idem = new IdempotencyService.Request(key,
                IdempotencyService.fingerprint("DEPOSIT", accountId, amount, description));
        return execute(actor, idem, () -> ledger.deposit(actor, accountId, amount, description, idem));
    }

    public Result withdraw(User actor, Long accountId, MoneyOperationRequest req, String key) {
        BigDecimal amount = normalize(req.amount());
        String description = clean(req.description());
        var idem = new IdempotencyService.Request(key,
                IdempotencyService.fingerprint("WITHDRAWAL", accountId, amount, description));
        return execute(actor, idem, () -> ledger.withdraw(actor, accountId, amount, description, idem));
    }

    public Result transfer(User actor, TransferRequest req, String key) {
        BigDecimal amount = normalize(req.amount());
        String description = clean(req.description());
        String destination = req.destinationAccountNumber().replaceAll("[\\s-]", "");
        var idem = new IdempotencyService.Request(key, IdempotencyService.fingerprint(
                "TRANSFER", req.sourceAccountId(), destination, amount, description));
        return execute(actor, idem,
                () -> ledger.transfer(actor, req.sourceAccountId(), destination, amount, description, idem));
    }

    private Result execute(User actor, IdempotencyService.Request idem, Supplier<Transaction> operation) {
        Optional<Result> previous = replay(actor, idem);
        if (previous.isPresent()) return previous.get();
        try {
            return new Result(mapper.toResponse(operation.get(), actor), false);
        } catch (OperationRejectedException rejected) {
            Transaction tx;
            try {
                tx = rejections.record(actor, idem, rejected);
            } catch (DataIntegrityViolationException race) {
                return replayOrThrow(actor, idem, race);
            }
            throw new BusinessRuleException(rejected.code, rejected.getMessage(), tx.getReference());
        } catch (DataIntegrityViolationException race) {
            // Otra petición con la misma clave ganó la carrera; este intento ya se revirtió por completo.
            return replayOrThrow(actor, idem, race);
        }
    }

    private Result replayOrThrow(User actor, IdempotencyService.Request idem, DataIntegrityViolationException cause) {
        return replay(actor, idem).orElseThrow(() -> cause);
    }

    private Optional<Result> replay(User actor, IdempotencyService.Request idem) {
        Optional<IdempotencyKey> stored = idempotency.findActive(actor.getId(), idem.key());
        if (stored.isEmpty()) return Optional.empty();
        if (!stored.get().getRequestHash().equals(idem.hash())) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key ya se usó con una petición diferente. Genera una clave nueva por operación");
        }
        Transaction tx = transactions.findByReference(stored.get().getTransactionReference())
                .orElseThrow(() -> new IllegalStateException("Movimiento de la clave de idempotencia no encontrado"));
        if (tx.getStatus() == TransactionStatus.REJECTED) {
            throw new BusinessRuleException(tx.getFailureCode(), tx.getFailureReason(), tx.getReference());
        }
        return Optional.of(new Result(mapper.toResponse(tx, actor), true));
    }

    /** La validación ya garantiza como máximo 2 decimales; se fija la escala para comparar huellas. */
    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String clean(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
