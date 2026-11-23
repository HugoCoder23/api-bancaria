package com.portafolio.banca.dto;

import com.portafolio.banca.entity.Currency;

import java.math.BigDecimal;
import java.util.List;

/** Indicadores para el panel de operadores. "Hoy" se calcula en UTC. */
public record SummaryResponse(long customers, long activeAccounts, long blockedAccounts,
                              long completedToday, long rejectedToday,
                              List<CurrencyTotal> balances, List<CurrencyTotal> volumeToday) {

    public record CurrencyTotal(Currency currency, BigDecimal amount, long count) {}
}
