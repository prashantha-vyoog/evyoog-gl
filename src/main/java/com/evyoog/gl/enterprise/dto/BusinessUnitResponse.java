package com.evyoog.gl.enterprise.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessUnitResponse(
        UUID id,
        UUID legalEntityId,
        String code,
        String name,
        String gstin,
        String stateCode,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
