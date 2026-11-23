package com.portafolio.banca.dto;

import com.portafolio.banca.entity.AuditResult;

import java.time.Instant;

public record AuditEventResponse(Long id, String actor, String action, String resource, AuditResult result,
                                 String detail, Instant createdAt) {
}
