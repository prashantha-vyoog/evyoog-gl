package com.evyoog.gl.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends EvyoogException {

    public DuplicateResourceException(String code, String message, String field) {
        super(code, message, HttpStatus.CONFLICT, field);
    }
}
