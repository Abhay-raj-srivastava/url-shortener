package com.abhay.url_shortener.exception;

import com.abhay.url_shortener.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for all REST controllers.
 *
 * Why a global handler?
 * - Ensures every error response follows the same ApiResponse envelope.
 * - Keeps controllers free of try/catch blocks — controllers only contain
 *   the happy path.
 * - A single place to add logging, alerting, or metrics on exceptions.
 *
 * Ordering: Spring picks the most-specific handler, so domain exceptions
 * are handled before the generic RuntimeException fallback.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------
    //  Domain exceptions
    // ---------------------------------------------------------------

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUrlNotFound(UrlNotFoundException ex) {
        log.warn("URL not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleUrlExpired(UrlExpiredException ex) {
        log.warn("URL expired: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(CustomAliasAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleAliasConflict(CustomAliasAlreadyExistsException ex) {
        log.warn("Alias conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidUrl(InvalidUrlException ex) {
        log.warn("Invalid URL: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCodeGenerationFailure(ShortCodeGenerationException ex) {
        log.error("Short code generation failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    // ---------------------------------------------------------------
    //  Validation exceptions
    // ---------------------------------------------------------------

    /**
     * Handles @Valid failures on @RequestBody objects.
     * Returns a map of field → error message in the data field so clients
     * know exactly which fields to fix.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .errorCode("VALIDATION_ERROR")
                        .data(errors)
                        .build());
    }

    /**
     * Handles path variable / request parameter type mismatches (e.g. letters
     * where a number is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format(
            "Parameter '%s' has invalid value '%s'", ex.getName(), ex.getValue()
        );
        log.warn("Type mismatch: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, "TYPE_MISMATCH"));
    }

    // ---------------------------------------------------------------
    //  Fallback
    // ---------------------------------------------------------------

    /**
     * Catch-all for any unhandled exception.
     * Logs the full stack trace (important for debugging) but returns
     * a generic message to the client (important for security — we never
     * leak internal stack traces over the wire).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR"));
    }
}
