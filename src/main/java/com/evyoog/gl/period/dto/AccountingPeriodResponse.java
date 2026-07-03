package com.evyoog.gl.period.dto;

import com.evyoog.gl.period.domain.AccountingPeriodType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingPeriodResponse(
        UUID id,
        UUID accountingCalendarId,
        String calendarName,
        String name,
        Integer periodNumber,
        String fiscalYear,
        AccountingPeriodType periodType,
        Integer quarterNumber,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive,
        Instant createdAt
) {
}
