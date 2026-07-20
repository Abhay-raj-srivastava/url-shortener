package com.abhay.url_shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a client requests a custom alias that is already taken.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CustomAliasAlreadyExistsException extends RuntimeException {

    private static final String ERROR_CODE = "ALIAS_ALREADY_EXISTS";

    public CustomAliasAlreadyExistsException(String alias) {
        super("Custom alias '" + alias + "' is already in use");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
