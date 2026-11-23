package com.portafolio.banca.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Evento de auditoría. Nunca contiene contraseñas, tokens ni números de cuenta completos. */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 80)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result;

    @Column(length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(String actor, String action, String resource, AuditResult result, String detail, Instant at) {
        this.actor = actor;
        this.action = action;
        this.resource = resource;
        this.result = result;
        this.detail = detail == null || detail.length() <= 255 ? detail : detail.substring(0, 255);
        this.createdAt = at;
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getResource() { return resource; }
    public AuditResult getResult() { return result; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
