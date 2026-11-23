package com.portafolio.banca.service;

import com.portafolio.banca.dto.*;
import com.portafolio.banca.dto.StatusChangeRequests.AccountStatusRequest;
import com.portafolio.banca.entity.*;
import com.portafolio.banca.exception.BusinessRuleException;
import com.portafolio.banca.exception.ResourceNotFoundException;
import com.portafolio.banca.mapper.BankMapper;
import com.portafolio.banca.repository.AccountRepository;
import com.portafolio.banca.repository.CustomerRepository;
import com.portafolio.banca.repository.Specs;
import com.portafolio.banca.repository.TransactionRepository;
import com.portafolio.banca.security.AccessPolicy;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.portafolio.banca.mapper.BankMapper.mask;

@Service
public class AccountService {

    /** Límite de cuentas abiertas (no cerradas) por cliente en la demo. */
    static final int MAX_ACCOUNTS_PER_CUSTOMER = 6;

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final IdentifierGenerator ids;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final BankMapper mapper;

    public AccountService(AccountRepository accounts, CustomerRepository customers, TransactionRepository transactions,
                          IdentifierGenerator ids, AccessPolicy policy, AuditService audit, BankMapper mapper) {
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.ids = ids;
        this.policy = policy;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public AccountResponse open(User actor, AccountRequest req) {
        Long customerId;
        if (actor.isStaff()) {
            if (req.customerId() == null) throw new IllegalArgumentException("customerId es obligatorio para operadores");
            customerId = req.customerId();
        } else {
            if (req.customerId() != null && !req.customerId().equals(actor.getCustomerId())) {
                throw new AccessDeniedException("Solo puedes abrir cuentas a tu nombre");
            }
            customerId = actor.getCustomerId();
        }
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("El cliente no existe"));
        if (!customer.isActive()) {
            throw new BusinessRuleException("CUSTOMER_BLOCKED", "El cliente está bloqueado y no puede abrir cuentas");
        }
        long open = accounts.count(Specs.accounts(customerId, AccountStatus.ACTIVE, null, null))
                + accounts.count(Specs.accounts(customerId, AccountStatus.BLOCKED, null, null));
        if (open >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new BusinessRuleException("ACCOUNT_LIMIT",
                    "El cliente ya tiene el máximo de " + MAX_ACCOUNTS_PER_CUSTOMER + " cuentas abiertas");
        }
        Account account = accounts.save(new Account(customer, ids.newAccountNumber(), req.type(), req.currency()));
        audit.record(actor, "ACCOUNT_OPEN", "account:" + account.getId(), AuditResult.SUCCESS,
                req.type() + " " + req.currency() + " " + mask(account.getNumber()));
        return mapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(User actor, Long id) {
        return mapper.toResponse(readable(actor, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(User actor, Long customerId, AccountStatus status, Currency currency,
                                              String number, Pageable pageable) {
        // Un cliente siempre ve solo sus cuentas, ignore lo que envíe en customerId.
        Long scope = actor.isStaff() ? customerId : actor.getCustomerId();
        return PageResponse.of(accounts.findAll(Specs.accounts(scope, status, currency, number), pageable),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountTransactionResponse> history(User actor, Long id, TransactionType type,
                                                            TransactionStatus status, Pageable pageable) {
        Account account = readable(actor, id);
        return PageResponse.of(transactions.findAll(Specs.accountHistory(id, type, status), pageable),
                t -> mapper.toHistoryLine(t, account, actor));
    }

    @Transactional(readOnly = true)
    public TransactionResponse transaction(User actor, String reference) {
        Transaction tx = transactions.findByReference(reference)
                .filter(t -> policy.canSee(actor, t))
                .orElseThrow(() -> new ResourceNotFoundException("La operación no existe"));
        return mapper.toResponse(tx, actor);
    }

    /** Bloquear, desbloquear o cerrar una cuenta (operadores). CLOSED es definitivo y exige saldo cero. */
    @Transactional
    public AccountResponse changeStatus(User actor, Long id, AccountStatusRequest req) {
        Account account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(AccessPolicy.ACCOUNT_NOT_FOUND));
        AccountStatus from = account.getStatus();
        if (from == AccountStatus.CLOSED) {
            throw new BusinessRuleException("ACCOUNT_CLOSED", "Una cuenta cerrada no puede cambiar de estado");
        }
        if (req.status() == from) return mapper.toResponse(account);
        if (req.status() == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw new BusinessRuleException("ACCOUNT_HAS_BALANCE",
                    "Para cerrar la cuenta su saldo debe ser 0.00 (saldo actual " + account.getBalance().toPlainString() + ")");
        }
        account.setStatus(req.status());
        audit.record(actor, "ACCOUNT_STATUS", "account:" + id, AuditResult.SUCCESS,
                from + " -> " + req.status() + (req.reason() == null ? "" : " · " + req.reason()));
        accounts.flush();
        return mapper.toResponse(accounts.findWithCustomerById(id).orElseThrow());
    }

    private Account readable(User actor, Long id) {
        Account account = accounts.findWithCustomerById(id)
                .orElseThrow(() -> new ResourceNotFoundException(AccessPolicy.ACCOUNT_NOT_FOUND));
        return policy.requireReadable(actor, account);
    }
}
