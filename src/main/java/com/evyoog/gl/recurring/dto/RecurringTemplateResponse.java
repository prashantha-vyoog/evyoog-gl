package com.evyoog.gl.recurring.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record RecurringTemplateResponse(
        UUID id,
        UUID legalEntityId,
        String legalEntityName,
        UUID ledgerId,
        String ledgerName,
        String name,
        String description,
        String frequency,
        Integer dayOfMonth,
        UUID startPeriodId,
        UUID endPeriodId,
        String journalSourceCode,
        String journalCategoryCode,
        String reference,
        List<RecurringLineResponse> lines,
        boolean isActive,
        Instant createdAt
) {
}
