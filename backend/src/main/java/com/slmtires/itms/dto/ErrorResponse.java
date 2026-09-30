package com.slmtires.itms.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error shape returned to the frontend. Never carries stack traces
 * or other internal detail - those go to the server log only.
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> fieldErrors
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, null);
    }

    public ErrorResponse(int status, String error, String message, String path, List<String> fieldErrors) {
        this(Instant.now(), status, error, message, path, fieldErrors);
    }
}
