package com.evyoog.gl.aie.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record BatchStatusResponse(
        UUID batchId,
        String batchReference,
        String eventId,
        String sourceSystem,
        String status,
        Integer totalLines,
        Integer validLines,
        Integer errorLines,
        String errorSummary,
        UUID journalHeaderId,
        Instant createdAt,
        Instant updatedAt
) {
}
