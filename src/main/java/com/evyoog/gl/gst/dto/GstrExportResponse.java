package com.evyoog.gl.gst.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record GstrExportResponse(
        UUID jobId,
        String returnType,
        String status,
        String periodName,
        String gstin,
        Instant generatedAt,
        Map<String, Object> data
) {
}
