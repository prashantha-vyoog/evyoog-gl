package com.evyoog.gl.auth.dto;

import lombok.Builder;

import java.util.Set;
import java.util.UUID;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        UUID legalEntityId,
        Set<String> permissions,
        boolean mustChangePwd
) {
}
