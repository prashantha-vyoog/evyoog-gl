package com.evyoog.gl.event.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record SlaEventResponse(
        UUID id,
        UUID ledgerId,
        UUID legalEntityId,
        UUID accountingPeriodId,
        Map<String, Object> eventPayload,
        String status,
        Instant createdAt
) {
}
