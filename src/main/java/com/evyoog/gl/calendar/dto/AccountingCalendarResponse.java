package com.evyoog.gl.calendar.dto;

import com.evyoog.gl.calendar.domain.PeriodType;

import java.time.Instant;
import java.util.UUID;

public record AccountingCalendarResponse(
        UUID id,
        UUID ledgerId,
        String ledgerName,
        String name,
        String description,
        Integer fiscalYearStartMonth,
        Integer fiscalYearStartDay,
        PeriodType periodType,
        Integer periodsPerYear,
        Boolean isActive,
        Integer generatedPeriodCount,
        String currentFiscalYear,
        Instant createdAt,
        Instant updatedAt
) {
    public AccountingCalendarResponse withGeneratedInfo(int generatedPeriodCount, String currentFiscalYear) {
        return new AccountingCalendarResponse(id, ledgerId, ledgerName, name, description, fiscalYearStartMonth,
                fiscalYearStartDay, periodType, periodsPerYear, isActive, generatedPeriodCount, currentFiscalYear,
                createdAt, updatedAt);
    }
}
