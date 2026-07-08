package com.evyoog.gl.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(

        @NotNull(message = "roleId is required")
        UUID roleId,

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId
) {
}
