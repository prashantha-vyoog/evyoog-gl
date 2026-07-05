package com.evyoog.gl.audit.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AuditTrailResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        String performedBy,
        Object beforeValue,
        Object afterValue,
        Instant createdAt
) {
}
