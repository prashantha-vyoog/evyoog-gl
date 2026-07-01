package com.evyoog.gl.enterprise.dto;

import com.evyoog.gl.common.audit.domain.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String entityName,
        UUID entityId,
        AuditAction action,
        String oldValue,
        String newValue,
        String performedBy,
        Instant performedAt
) {
}
