package com.evyoog.gl.enterprise.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryOrganisationResponse(
        UUID id,
        UUID businessUnitId,
        String code,
        String name,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
