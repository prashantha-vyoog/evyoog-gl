package com.evyoog.gl.aie.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record AieImportResponse(
        UUID batchId,
        String batchReference,
        String eventId,
        // POSTED, FAILED
        String status,
        Integer totalLines,
        Integer validLines,
        Integer errorLines,
        UUID journalHeaderId,
        String journalNumber,
        String message,
        List<AieLineErrorResponse> errors
) {
}
