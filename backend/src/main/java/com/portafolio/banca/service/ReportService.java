package com.portafolio.banca.service;

import com.portafolio.banca.dto.SummaryResponse;
import com.portafolio.banca.dto.SummaryResponse.CurrencyTotal;
import com.portafolio.banca.entity.AccountStatus;
import com.portafolio.banca.entity.Currency;
import com.portafolio.banca.entity.TransactionStatus;
import com.portafolio.banca.repository.AccountRepository;
import com.portafolio.banca.repository.CustomerRepository;
import com.portafolio.banca.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ReportService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;

    public ReportService(CustomerRepository customers, AccountRepository accounts, TransactionRepository transactions,
                         Clock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary() {
        Instant today = LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new SummaryResponse(
                customers.count(),
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.BLOCKED),
                transactions.countByStatusAndCreatedAtGreaterThanEqual(TransactionStatus.COMPLETED, today),
                transactions.countByStatusAndCreatedAtGreaterThanEqual(TransactionStatus.REJECTED, today),
                totals(accounts.balancesByCurrency()),
                totals(transactions.completedVolumeSince(today)));
    }

    private static List<CurrencyTotal> totals(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new CurrencyTotal((Currency) r[0], ((BigDecimal) r[1]).setScale(2), ((Number) r[2]).longValue()))
                .toList();
    }
}
