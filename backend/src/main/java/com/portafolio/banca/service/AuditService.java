package com.portafolio.banca.service;

import com.portafolio.banca.dto.AuditEventResponse;
import com.portafolio.banca.dto.PageResponse;
import com.portafolio.banca.entity.AuditEvent;
import com.portafolio.banca.entity.AuditResult;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.mapper.BankMapper;
import com.portafolio.banca.repository.AuditEventRepository;
import com.portafolio.banca.repository.Specs;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Registro de auditoría. Regla: aquí nunca se escriben contraseñas, tokens ni números de cuenta completos.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final BankMapper mapper;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, BankMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Se une a la transacción actual: si la operación se revierte, su evento de éxito también. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, String action, String resource, AuditResult result, String detail) {
        repository.save(new AuditEvent(actor.getEmail(), action, resource, result, detail, clock.instant()));
    }

    /** En una transacción propia: persiste aunque la operación que lo origina falle (rechazos, logins fallidos). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(String actor, String action, String resource, AuditResult result, String detail) {
        repository.save(new AuditEvent(actor, action, resource, result, detail, clock.instant()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(String action, String actor, AuditResult result, Pageable pageable) {
        return PageResponse.of(repository.findAll(Specs.audit(action, actor, result), pageable), mapper::toResponse);
    }
}
