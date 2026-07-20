package com.abhay.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a URL passes Bean Validation but fails deeper semantic checks
 * (e.g. blocked host, non-resolvable domain).
 * Maps to HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidUrlException extends RuntimeException {

    private static final String ERROR_CODE = "INVALID_URL";

    public InvalidUrlException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
