package com.portafolio.banca.dto;

import com.portafolio.banca.entity.Currency;
import com.portafolio.banca.entity.TransactionStatus;
import com.portafolio.banca.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Comprobante de una operación. Los números de cuentas ajenas se devuelven enmascarados
 * y sus identificadores y saldos se omiten.
 */
public record TransactionResponse(
        String reference, TransactionType type, TransactionStatus status, BigDecimal amount, Currency currency,
        Long sourceAccountId, String sourceAccountNumber, BigDecimal sourceBalanceAfter,
        Long destinationAccountId, String destinationAccountNumber, BigDecimal destinationBalanceAfter,
        String description, String failureCode, String failureReason, Instant createdAt) {
}
