package com.evyoog.gl.calendar.dto;

import com.evyoog.gl.calendar.domain.PeriodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCalendarRequest(

        @NotNull(message = "ledgerId is required")
        UUID ledgerId,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @Min(value = 1, message = "fiscalYearStartMonth must be between 1 and 12")
        @Max(value = 12, message = "fiscalYearStartMonth must be between 1 and 12")
        Integer fiscalYearStartMonth,

        @Min(value = 1, message = "fiscalYearStartDay must be between 1 and 31")
        @Max(value = 31, message = "fiscalYearStartDay must be between 1 and 31")
        Integer fiscalYearStartDay,

        PeriodType periodType,

        // Which fiscal year to generate the initial set of periods for, e.g. 2025 -> APR-2025..MAR-2026
        Integer initialFiscalYear
) {
}
