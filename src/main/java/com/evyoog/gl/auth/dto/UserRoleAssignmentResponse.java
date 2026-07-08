package com.evyoog.gl.auth.dto;

import java.util.UUID;

public record UserRoleAssignmentResponse(
        UUID userRoleId,
        UUID roleId,
        String roleCode,
        String roleName,
        UUID legalEntityId,
        String legalEntityCode
) {
}
