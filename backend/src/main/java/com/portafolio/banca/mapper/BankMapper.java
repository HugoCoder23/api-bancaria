package com.portafolio.banca.mapper;

import com.portafolio.banca.dto.*;
import com.portafolio.banca.dto.AccountTransactionResponse.Direction;
import com.portafolio.banca.entity.*;
import com.portafolio.banca.security.AccessPolicy;
import org.springframework.stereotype.Component;

/** Conversión entidad → DTO. Enmascara los datos de cuentas que el usuario no tiene derecho a ver. */
@Component
public class BankMapper {

    private final AccessPolicy policy;

    public BankMapper(AccessPolicy policy) {
        this.policy = policy;
    }

    /** "990000001234" → "•••• 1234". */
    public static String mask(String accountNumber) {
        if (accountNumber == null) return null;
        return "•••• " + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }

    public UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getCustomerId());
    }

    public CustomerResponse toResponse(Customer c, Long accountCount) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getStatus(),
                accountCount, c.getCreatedAt());
    }

    public AccountResponse toResponse(Account a) {
        Customer c = a.getCustomer();
        return new AccountResponse(a.getId(), a.getNumber(), a.getType(), a.getCurrency(), a.getBalance(),
                a.getStatus(), a.getOpenedAt(), c.getId(), c.getFullName());
    }

    public TransactionResponse toResponse(Transaction t, User viewer) {
        Account src = t.getSourceAccount();
        Account dst = t.getDestinationAccount();
        boolean seeSrc = policy.canSee(viewer, src);
        boolean seeDst = policy.canSee(viewer, dst);
        return new TransactionResponse(
                t.getReference(), t.getType(), t.getStatus(), t.getAmount(), t.getCurrency(),
                seeSrc ? src.getId() : null,
                src == null ? null : seeSrc ? src.getNumber() : mask(src.getNumber()),
                seeSrc ? t.getSourceBalanceAfter() : null,
                seeDst ? dst.getId() : null,
                dst == null ? null : seeDst ? dst.getNumber() : mask(dst.getNumber()),
                seeDst ? t.getDestinationBalanceAfter() : null,
                t.getDescription(), t.getFailureCode(), t.getFailureReason(), t.getCreatedAt());
    }

    /** Línea del estado de cuenta desde el punto de vista de {@code account}. */
    public AccountTransactionResponse toHistoryLine(Transaction t, Account account, User viewer) {
        boolean debit = t.getSourceAccount() != null && t.getSourceAccount().getId().equals(account.getId());
        Account counterparty = debit ? t.getDestinationAccount() : t.getSourceAccount();
        String counterpartyNumber = counterparty == null ? null
                : policy.canSee(viewer, counterparty) ? counterparty.getNumber() : mask(counterparty.getNumber());
        return new AccountTransactionResponse(
                t.getReference(), t.getType(), t.getStatus(), debit ? Direction.DEBIT : Direction.CREDIT,
                t.getAmount(), debit ? t.getSourceBalanceAfter() : t.getDestinationBalanceAfter(),
                counterpartyNumber, t.getDescription(), t.getFailureReason(), t.getCreatedAt());
    }

    public AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActor(), e.getAction(), e.getResource(), e.getResult(),
                e.getDetail(), e.getCreatedAt());
    }
}
