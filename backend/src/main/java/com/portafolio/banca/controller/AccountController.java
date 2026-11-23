package com.portafolio.banca.controller;

import com.portafolio.banca.dto.*;
import com.portafolio.banca.dto.StatusChangeRequests.AccountStatusRequest;
import com.portafolio.banca.entity.AccountStatus;
import com.portafolio.banca.entity.Currency;
import com.portafolio.banca.entity.TransactionStatus;
import com.portafolio.banca.entity.TransactionType;
import com.portafolio.banca.security.CurrentUserProvider;
import com.portafolio.banca.service.AccountService;
import com.portafolio.banca.service.OperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

import static com.portafolio.banca.controller.IdempotentResponses.*;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "3. Cuentas y operaciones")
public class AccountController {

    private static final Map<String, String> SORTS = Map.of(
            "openedAt", "openedAt", "balance", "balance", "number", "number", "customer", "customer.fullName");

    private final AccountService accounts;
    private final OperationService operations;
    private final CurrentUserProvider currentUser;

    public AccountController(AccountService accounts, OperationService operations, CurrentUserProvider currentUser) {
        this.accounts = accounts;
        this.operations = operations;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Abrir cuenta",
            description = "Número único generado por el sistema, saldo inicial 0.00. Un cliente abre cuentas a su "
                    + "nombre; un operador debe indicar customerId.")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody AccountRequest request) {
        AccountResponse created = accounts.open(currentUser.get(), request);
        return ResponseEntity.created(URI.create("/api/accounts/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar cuentas",
            description = "Un cliente ve solo las suyas. Los operadores pueden filtrar por cliente, estado, moneda o número.")
    public PageResponse<AccountResponse> list(@RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) AccountStatus status,
                                              @RequestParam(required = false) Currency currency,
                                              @RequestParam(required = false) String number,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String sort) {
        return accounts.list(currentUser.get(), customerId, status, currency, number,
                PageRequests.of(page, size, sort, SORTS, Sort.by("openedAt", "id")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cuenta y saldo", description = "Responde 404 si la cuenta no pertenece al usuario.")
    public AccountResponse get(@PathVariable Long id) {
        return accounts.get(currentUser.get(), id);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Historial paginado de movimientos",
            description = "Del más reciente al más antiguo. Incluye los intentos rechazados (status=REJECTED).")
    public PageResponse<AccountTransactionResponse> history(@PathVariable Long id,
                                                            @RequestParam(required = false) TransactionType type,
                                                            @RequestParam(required = false) TransactionStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        PageRequests.of(page, size, null, Map.of(), Sort.unsorted()); // valida page/size
        return accounts.history(currentUser.get(), id, type, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Simular depósito (titular)", description = "Requiere cabecera Idempotency-Key.")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable Long id,
            @Parameter(description = KEY_DOC, required = true)
            @RequestHeader(KEY_HEADER) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody MoneyOperationRequest request) {
        return IdempotentResponses.of(operations.deposit(currentUser.get(), id, request, idempotencyKey));
    }

    @PostMapping("/{id}/withdrawals")
    @Operation(summary = "Simular retiro (titular)",
            description = "Requiere Idempotency-Key. Se rechaza (422 INSUFFICIENT_FUNDS) si supera el saldo.")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable Long id,
            @Parameter(description = KEY_DOC, required = true)
            @RequestHeader(KEY_HEADER) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody MoneyOperationRequest request) {
        return IdempotentResponses.of(operations.withdraw(currentUser.get(), id, request, idempotencyKey));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Operation(summary = "Bloquear, desbloquear o cerrar cuenta (operador)",
            description = "CLOSED es definitivo y exige saldo 0.00.")
    public AccountResponse changeStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest request) {
        return accounts.changeStatus(currentUser.get(), id, request);
    }
}
