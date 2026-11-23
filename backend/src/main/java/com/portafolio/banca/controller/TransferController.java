package com.portafolio.banca.controller;

import com.portafolio.banca.dto.TransactionResponse;
import com.portafolio.banca.dto.TransferRequest;
import com.portafolio.banca.security.CurrentUserProvider;
import com.portafolio.banca.service.AccountService;
import com.portafolio.banca.service.OperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.portafolio.banca.controller.IdempotentResponses.*;

@RestController
@RequestMapping("/api")
@Tag(name = "4. Transferencias y comprobantes")
public class TransferController {

    private final OperationService operations;
    private final AccountService accounts;
    private final CurrentUserProvider currentUser;

    public TransferController(OperationService operations, AccountService accounts, CurrentUserProvider currentUser) {
        this.operations = operations;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transferir entre cuentas",
            description = "Débito y crédito atómicos en una sola transacción. Origen y destino deben ser distintas, "
                    + "activas y de la misma moneda. Requiere Idempotency-Key.")
    public ResponseEntity<TransactionResponse> transfer(
            @Parameter(description = KEY_DOC, required = true)
            @RequestHeader(KEY_HEADER) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return IdempotentResponses.of(operations.transfer(currentUser.get(), request, idempotencyKey));
    }

    @GetMapping("/transactions/{reference}")
    @Operation(summary = "Consultar el resultado de una operación",
            description = "Visible para el titular de alguna de las cuentas involucradas y para operadores.")
    public TransactionResponse transaction(@PathVariable String reference) {
        return accounts.transaction(currentUser.get(), reference.trim().toUpperCase());
    }
}
