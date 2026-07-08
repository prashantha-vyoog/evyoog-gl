package com.evyoog.gl.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RefreshRequest(

        @NotBlank(message = "refreshToken is required")
        String refreshToken,

        UUID legalEntityId
) {
}
