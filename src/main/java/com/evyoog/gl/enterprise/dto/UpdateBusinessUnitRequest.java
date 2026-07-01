package com.evyoog.gl.enterprise.dto;

import jakarta.validation.constraints.Size;

public record UpdateBusinessUnitRequest(

        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        String gstin,

        @Size(max = 2, message = "stateCode must be at most 2 characters")
        String stateCode
) {
}
