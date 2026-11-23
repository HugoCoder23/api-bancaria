package com.portafolio.banca.controller;

import com.portafolio.banca.dto.AuditEventResponse;
import com.portafolio.banca.dto.PageResponse;
import com.portafolio.banca.dto.SummaryResponse;
import com.portafolio.banca.entity.AuditResult;
import com.portafolio.banca.service.AuditService;
import com.portafolio.banca.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "5. Operación y auditoría")
public class AdminController {

    private final ReportService reports;
    private final AuditService audit;

    public AdminController(ReportService reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Indicadores generales (operador)")
    public SummaryResponse summary() {
        return reports.summary();
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bitácora de auditoría (administrador)",
            description = "Logins, altas, cambios de estado y operaciones (exitosas y rechazadas). Sin secretos.")
    public PageResponse<AuditEventResponse> auditEvents(@RequestParam(required = false) String action,
                                                        @RequestParam(required = false) String actor,
                                                        @RequestParam(required = false) AuditResult result,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return audit.list(action, actor, result, PageRequests.of(page, size, null, Map.of(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
}
