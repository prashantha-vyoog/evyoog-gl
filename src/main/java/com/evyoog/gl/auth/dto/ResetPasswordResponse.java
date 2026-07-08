package com.evyoog.gl.auth.dto;

import java.util.UUID;

public record ResetPasswordResponse(
        UUID userId,
        String temporaryPassword
) {
}
