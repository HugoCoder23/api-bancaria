package com.portafolio.banca.dto;

import com.portafolio.banca.entity.AccountStatus;
import com.portafolio.banca.entity.AccountType;
import com.portafolio.banca.entity.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(Long id, String number, AccountType type, Currency currency, BigDecimal balance,
                              AccountStatus status, Instant openedAt, Long customerId, String customerName) {
}
