package com.evyoog.gl.audit.mapper;

import com.evyoog.gl.audit.dto.AuditTrailResponse;
import com.evyoog.gl.common.audit.domain.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AuditLog.oldValue/newValue are stored as JSON strings (see AuditService#toJson).
 * This mapper re-parses them into structured Object graphs so API consumers get
 * real JSON in the response instead of a doubly-escaped string.
 */
@Component
@RequiredArgsConstructor
public class AuditTrailMapper {

    private final ObjectMapper objectMapper;

    public AuditTrailResponse toResponse(AuditLog log) {
        return AuditTrailResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityName())
                .entityId(log.getEntityId())
                .action(log.getAction().name())
                .performedBy(log.getPerformedBy())
                .beforeValue(parseJson(log.getOldValue()))
                .afterValue(parseJson(log.getNewValue()))
                .createdAt(log.getPerformedAt())
                .build();
    }

    private Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
