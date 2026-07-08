package com.evyoog.gl.auth.dto;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String module,
        String function,
        String action,
        String description
) {
}
