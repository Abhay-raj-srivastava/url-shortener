package com.abhay.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a short code does not exist or belongs to an inactive/deleted link.
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UrlNotFoundException extends RuntimeException {

    private static final String ERROR_CODE = "URL_NOT_FOUND";

    public UrlNotFoundException(String shortCode) {
        super("No active URL found for short code: " + shortCode);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
