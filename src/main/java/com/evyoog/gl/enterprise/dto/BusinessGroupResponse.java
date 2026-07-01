package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.EsMode;

import java.time.Instant;
import java.util.UUID;

public record BusinessGroupResponse(
        UUID id,
        UUID consumptionContextId,
        String code,
        String name,
        EsMode esMode,
        String defaultCurrency,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
