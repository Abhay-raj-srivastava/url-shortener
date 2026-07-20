package com.abhay.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the service fails to generate a unique short code after
 * the maximum number of retry attempts.
 *
 * This should be extremely rare in practice (probability of N consecutive
 * collisions drops exponentially), but it is important to handle it
 * explicitly rather than letting the DB throw a unique-constraint violation.
 *
 * Maps to HTTP 503 Service Unavailable — the client can retry later.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ShortCodeGenerationException extends RuntimeException {

    private static final String ERROR_CODE = "CODE_GENERATION_FAILED";

    public ShortCodeGenerationException(int maxAttempts) {
        super("Failed to generate a unique short code after " + maxAttempts + " attempts");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
