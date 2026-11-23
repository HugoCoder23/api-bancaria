package com.portafolio.banca.controller;

import com.portafolio.banca.dto.CustomerRequest;
import com.portafolio.banca.dto.CustomerResponse;
import com.portafolio.banca.dto.PageResponse;
import com.portafolio.banca.dto.StatusChangeRequests.CustomerStatusRequest;
import com.portafolio.banca.entity.CustomerStatus;
import com.portafolio.banca.security.CurrentUserProvider;
import com.portafolio.banca.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "2. Clientes")
public class CustomerController {

    private static final Map<String, String> SORTS = Map.of("name", "fullName", "createdAt", "createdAt", "email", "email");

    private final CustomerService service;
    private final CurrentUserProvider currentUser;

    public CustomerController(CustomerService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Crear cliente de prueba (operador)",
            description = "Crea el cliente ficticio y su usuario de acceso con rol CUSTOMER.")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = service.create(currentUser.get(), request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Listar clientes (operador)", description = "Búsqueda por nombre o email. sort: name|createdAt|email,asc|desc")
    public PageResponse<CustomerResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) CustomerStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sort) {
        return service.list(q, status, PageRequests.of(page, size, sort, SORTS, Sort.by("fullName", "id")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cliente", description = "Un cliente solo puede consultar su propio registro.")
    public CustomerResponse get(@PathVariable Long id) {
        return service.get(currentUser.get(), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Bloquear o desbloquear cliente (operador)",
            description = "Un cliente bloqueado puede consultar pero no operar ni recibir transferencias.")
    public CustomerResponse changeStatus(@PathVariable Long id, @Valid @RequestBody CustomerStatusRequest request) {
        return service.changeStatus(currentUser.get(), id, request);
    }
}
