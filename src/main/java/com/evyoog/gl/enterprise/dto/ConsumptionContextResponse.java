package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.enterprise.domain.SegmentType;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionContextResponse(
        UUID id,
        SegmentType segmentType,
        String code,
        String name,
        String status,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
}
