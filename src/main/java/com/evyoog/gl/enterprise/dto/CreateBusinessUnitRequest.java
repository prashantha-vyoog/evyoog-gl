package com.evyoog.gl.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBusinessUnitRequest(

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must be at most 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,

        String gstin,

        @Size(max = 2, message = "stateCode must be at most 2 characters")
        String stateCode
) {
}
