package com.portafolio.banca.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce excepciones a respuestas ApiError coherentes. Los logs nunca incluyen cuerpos de petición,
 * contraseñas ni tokens.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID = "La solicitud contiene datos inválidos";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.add(new ApiError.FieldError(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.add(new ApiError.FieldError(ge.getObjectName(), ge.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", INVALID, req, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        ex.getAllValidationResults().forEach(r -> {
            if (r instanceof org.springframework.validation.method.ParameterErrors errors) {
                // Cuerpo @Valid: se informan los campos concretos del objeto (amount, description...).
                errors.getFieldErrors().forEach(fe -> fields.add(new ApiError.FieldError(fe.getField(), fe.getDefaultMessage())));
                errors.getGlobalErrors().forEach(ge -> fields.add(new ApiError.FieldError(ge.getObjectName(), ge.getDefaultMessage())));
                return;
            }
            String name = r.getMethodParameter().getParameterName();
            var header = r.getMethodParameter().getParameterAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
            if (header != null && !header.value().isEmpty()) name = header.value();
            for (var error : r.getResolvableErrors()) {
                fields.add(new ApiError.FieldError(name, error.getDefaultMessage()));
            }
        });
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", INVALID, req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", INVALID, req, fields);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName()) ? "IDEMPOTENCY_KEY_REQUIRED" : "BAD_REQUEST";
        return build(HttpStatus.BAD_REQUEST, code,
                "Falta la cabecera obligatoria '" + ex.getHeaderName() + "'", req, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest req) {
        String message;
        if (ex instanceof HttpMessageNotReadableException) {
            message = "El cuerpo de la solicitud no es válido o tiene un formato incorrecto";
        } else if (ex instanceof MethodArgumentTypeMismatchException e) {
            message = "El parámetro '" + e.getName() + "' tiene un valor inválido";
        } else {
            message = ex.getMessage();
        }
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, req, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ruta no encontrada", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessRuleException ex, HttpServletRequest req) {
        ApiError body = new ApiError(Instant.now(), 422, HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getCode(), ex.getMessage(), req.getRequestURI(), ex.getReference(), null);
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class,
            PessimisticLockingFailureException.class, PessimisticLockException.class})
    ResponseEntity<ApiError> handleLock(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "La cuenta está siendo modificada por otra operación. Intenta de nuevo", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "La operación viola una restricción de datos (duplicado o referencia inválida)", req, null);
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    ResponseEntity<ApiError> handleBadCredentials(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email o contraseña incorrectos", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        String message = ex.getMessage() != null && !ex.getMessage().equals("Access Denied")
                ? ex.getMessage() : "No tienes permisos para realizar esta acción";
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", message, req, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error inesperado. La operación no se aplicó", req, null);
    }

    static ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest req,
                                          List<ApiError.FieldError> fields) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                req.getRequestURI(), null, fields == null || fields.isEmpty() ? null : fields);
        return ResponseEntity.status(status).body(body);
    }
}
