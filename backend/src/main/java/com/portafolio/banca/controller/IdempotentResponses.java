package com.portafolio.banca.controller;

import com.portafolio.banca.dto.TransactionResponse;
import com.portafolio.banca.service.OperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/** Respuesta común de las operaciones de dinero: 201 + Location del comprobante + marca de reintento. */
final class IdempotentResponses {

    static final String KEY_HEADER = "Idempotency-Key";
    static final String KEY_PATTERN = "^[A-Za-z0-9_-]{8,100}$";
    static final String KEY_MESSAGE = "Debe tener entre 8 y 100 caracteres: letras, dígitos, '-' o '_' (p. ej. un UUID)";
    static final String KEY_DOC = "Clave única por operación (p. ej. un UUID). Repetir la petición con la misma clave "
            + "devuelve el resultado original sin duplicar el movimiento.";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private IdempotentResponses() {}

    static ResponseEntity<TransactionResponse> of(OperationService.Result result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/transactions/" + result.transaction().reference()))
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.transaction());
    }
}
