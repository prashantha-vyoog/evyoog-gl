package com.evyoog.gl.recurring.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RecurringRunResponse(
        UUID id,
        UUID templateId,
        String templateName,
        UUID journalHeaderId,
        String journalNumber,
        UUID accountingPeriodId,
        String periodName,
        Instant generatedAt,
        String generatedBy
) {
}
