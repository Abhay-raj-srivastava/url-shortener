package com.abhay.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a valid short code is found but the link has passed its expiry date.
 * Maps to HTTP 410 Gone — semantically more accurate than 404 for expired resources.
 */
@ResponseStatus(HttpStatus.GONE)
public class UrlExpiredException extends RuntimeException {

    private static final String ERROR_CODE = "URL_EXPIRED";

    public UrlExpiredException(String shortCode) {
        super("The URL for short code '" + shortCode + "' has expired");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
