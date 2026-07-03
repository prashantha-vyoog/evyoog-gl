package com.evyoog.gl.periodstatus.dto;

import com.evyoog.gl.periodstatus.domain.PeriodStatusEnum;

import java.time.Instant;
import java.util.UUID;

public record PeriodStatusResponse(
        UUID id,
        UUID legalEntityId,
        String legalEntityName,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        PeriodStatusEnum status,
        Instant openedAt,
        String openedBy,
        Instant closedAt,
        String closedBy,
        Instant lockedAt,
        String lockedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
