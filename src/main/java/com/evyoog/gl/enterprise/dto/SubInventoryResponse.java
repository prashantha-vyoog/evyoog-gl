package com.evyoog.gl.enterprise.dto;

import java.time.Instant;
import java.util.UUID;

public record SubInventoryResponse(
        UUID id,
        UUID inventoryOrganisationId,
        String code,
        String name,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
