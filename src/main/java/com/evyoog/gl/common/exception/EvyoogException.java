package com.evyoog.gl.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EvyoogException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final String field;

    public EvyoogException(String code, String message) {
        this(code, message, HttpStatus.CONFLICT, null);
    }

    public EvyoogException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public EvyoogException(String code, String message, HttpStatus status, String field) {
        super(message);
        this.code = code;
        this.status = status;
        this.field = field;
    }
}
