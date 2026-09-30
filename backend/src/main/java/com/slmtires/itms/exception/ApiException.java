package com.slmtires.itms.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that should be translated into a specific,
 * user-friendly HTTP response by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
