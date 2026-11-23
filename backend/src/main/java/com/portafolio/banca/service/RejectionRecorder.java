package com.portafolio.banca.service;

import com.portafolio.banca.entity.Account;
import com.portafolio.banca.entity.AuditResult;
import com.portafolio.banca.entity.Transaction;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.repository.AccountRepository;
import com.portafolio.banca.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Registra un intento rechazado como movimiento REJECTED (sin tocar saldos), en una transacción nueva
 * posterior al rollback de la operación. También guarda su Idempotency-Key: repetir la misma petición
 * devuelve el mismo rechazo en lugar de reevaluarla.
 */
@Service
class RejectionRecorder {

    private static final Logger log = LoggerFactory.getLogger(RejectionRecorder.class);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final IdentifierGenerator ids;
    private final Clock clock;

    RejectionRecorder(TransactionRepository transactions, AccountRepository accounts, IdempotencyService idempotency,
                      AuditService audit, IdentifierGenerator ids, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.idempotency = idempotency;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public Transaction record(User actor, IdempotencyService.Request idem, OperationRejectedException ex) {
        Account source = ex.sourceAccountId == null ? null : accounts.getReferenceById(ex.sourceAccountId);
        Account destination = ex.destinationAccountId == null ? null : accounts.getReferenceById(ex.destinationAccountId);
        // Para una transferencia a la misma cuenta, ambas referencias apuntan a la misma fila.
        Account any = source != null ? source : destination;
        any.getCurrency(); // inicializa el proxy para obtener la moneda

        Transaction tx = transactions.save(Transaction.rejected(ids.newReference(), ex.type, ex.amount, source,
                destination, ex.description, actor, clock.instant(), ex.code, ex.getMessage()));
        audit.record(actor, ex.type.name(), "transaction:" + tx.getReference(), AuditResult.REJECTED, ex.code);
        idempotency.save(actor, idem, HttpStatus.UNPROCESSABLE_ENTITY.value(), tx.getReference());
        log.info("{} {} rechazada: {}", ex.type, tx.getReference(), ex.code);
        return tx;
    }
}
