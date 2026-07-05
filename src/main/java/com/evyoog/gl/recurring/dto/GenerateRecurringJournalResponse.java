package com.evyoog.gl.recurring.dto;

import com.evyoog.gl.posting.domain.JournalStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GenerateRecurringJournalResponse(
        UUID templateId,
        String templateName,
        UUID journalHeaderId,
        String journalNumber,
        UUID targetPeriodId,
        String targetPeriodName,
        JournalStatus journalStatus,
        Instant generatedAt,
        String message
) {
}
