package com.portafolio.banca.service;

import com.portafolio.banca.entity.TransactionType;

import java.math.BigDecimal;

/**
 * Rechazo de una operación de dinero por una regla de negocio, detectado dentro de la transacción del
 * LedgerService (que por tanto se revierte). OperationService lo captura y registra el intento como
 * movimiento REJECTED en una transacción nueva. Uso interno del paquete de servicios.
 */
class OperationRejectedException extends RuntimeException {

    final String code;
    final TransactionType type;
    final BigDecimal amount;
    final Long sourceAccountId;
    final Long destinationAccountId;
    final String description;

    OperationRejectedException(String code, String message, TransactionType type, BigDecimal amount,
                               Long sourceAccountId, Long destinationAccountId, String description) {
        super(message);
        this.code = code;
        this.type = type;
        this.amount = amount;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.description = description;
    }
}
