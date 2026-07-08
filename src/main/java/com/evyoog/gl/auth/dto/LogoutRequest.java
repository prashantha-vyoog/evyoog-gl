package com.evyoog.gl.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(

        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
