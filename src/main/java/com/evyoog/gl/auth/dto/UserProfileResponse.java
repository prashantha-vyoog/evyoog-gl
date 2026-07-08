package com.evyoog.gl.auth.dto;

import lombok.Builder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        boolean mustChangePwd,
        List<UserRoleAssignmentResponse> roles,
        Set<String> permissions
) {
}
