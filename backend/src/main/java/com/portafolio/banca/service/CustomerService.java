package com.portafolio.banca.service;

import com.portafolio.banca.dto.CustomerRequest;
import com.portafolio.banca.dto.CustomerResponse;
import com.portafolio.banca.dto.PageResponse;
import com.portafolio.banca.dto.StatusChangeRequests.CustomerStatusRequest;
import com.portafolio.banca.entity.*;
import com.portafolio.banca.exception.ConflictException;
import com.portafolio.banca.exception.ResourceNotFoundException;
import com.portafolio.banca.mapper.BankMapper;
import com.portafolio.banca.repository.AccountRepository;
import com.portafolio.banca.repository.CustomerRepository;
import com.portafolio.banca.repository.Specs;
import com.portafolio.banca.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final BankMapper mapper;

    public CustomerService(CustomerRepository customers, UserRepository users, AccountRepository accounts,
                           PasswordEncoder passwordEncoder, AuditService audit, BankMapper mapper) {
        this.customers = customers;
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** Alta de un cliente ficticio y de su usuario de acceso (rol CUSTOMER). */
    @Transactional
    public CustomerResponse create(User actor, CustomerRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmailIgnoreCase(email) || users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "Ya existe un usuario con ese email");
        }
        Customer customer = customers.save(new Customer(req.fullName().trim(), email, req.phone().trim()));
        users.save(new User(customer.getFullName(), email, passwordEncoder.encode(req.password()), Role.CUSTOMER, customer));
        audit.record(actor, "CUSTOMER_CREATE", "customer:" + customer.getId(), AuditResult.SUCCESS, null);
        return mapper.toResponse(customer, 0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String q, CustomerStatus status, Pageable pageable) {
        return PageResponse.of(customers.findAll(Specs.customers(q, status), pageable),
                c -> mapper.toResponse(c, accounts.countByCustomerId(c.getId())));
    }

    /** Un cliente solo puede consultarse a sí mismo; el staff puede consultar a cualquiera. */
    @Transactional(readOnly = true)
    public CustomerResponse get(User actor, Long id) {
        if (!actor.isStaff() && !id.equals(actor.getCustomerId())) {
            throw new ResourceNotFoundException("El cliente no existe");
        }
        Customer c = find(id);
        return mapper.toResponse(c, accounts.countByCustomerId(id));
    }

    @Transactional
    public CustomerResponse changeStatus(User actor, Long id, CustomerStatusRequest req) {
        Customer c = find(id);
        CustomerStatus from = c.getStatus();
        if (from != req.status()) {
            c.setStatus(req.status());
            audit.record(actor, "CUSTOMER_STATUS", "customer:" + id, AuditResult.SUCCESS,
                    from + " -> " + req.status() + (req.reason() == null ? "" : " · " + req.reason()));
        }
        return mapper.toResponse(c, accounts.countByCustomerId(id));
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("El cliente no existe"));
    }
}
