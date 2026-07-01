package com.evyoog.gl.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends EvyoogException {

    public ResourceNotFoundException(String entityName, UUID id) {
        super("RESOURCE_NOT_FOUND", entityName + " not found: " + id, HttpStatus.NOT_FOUND);
    }
}
