package com.portafolio.banca.dto;

import com.portafolio.banca.entity.TransactionStatus;
import com.portafolio.banca.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Línea del estado de cuenta, vista desde una cuenta concreta.
 *
 * @param direction    CREDIT (entra dinero) o DEBIT (sale dinero) respecto a esta cuenta
 * @param balanceAfter saldo de esta cuenta tras el movimiento (nulo si fue rechazado)
 */
public record AccountTransactionResponse(
        String reference, TransactionType type, TransactionStatus status, Direction direction, BigDecimal amount,
        BigDecimal balanceAfter, String counterpartyAccountNumber, String description, String failureReason,
        Instant createdAt) {

    public enum Direction { CREDIT, DEBIT }
}
