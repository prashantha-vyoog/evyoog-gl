package com.evyoog.gl.auth.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRoleRequest(

        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        Set<String> permissionCodes,

        Boolean isActive
) {
}
