package com.evyoog.gl.common.exception;

import java.time.Instant;

public record ErrorResponse(
        int status,
        String code,
        String message,
        String field,
        Instant timestamp
) {

    public static ErrorResponse of(int status, String code, String message, String field) {
        return new ErrorResponse(status, code, message, field, Instant.now());
    }
}
