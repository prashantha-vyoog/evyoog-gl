package com.evyoog.gl.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends EvyoogException {

    public ValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String code, String message, String field) {
        super(code, message, HttpStatus.BAD_REQUEST, field);
    }
}
