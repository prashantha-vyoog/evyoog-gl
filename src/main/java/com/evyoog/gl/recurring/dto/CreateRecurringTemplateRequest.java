package com.evyoog.gl.recurring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateRecurringTemplateRequest(
        @NotNull UUID legalEntityId,
        @NotNull UUID ledgerId,
        @NotBlank String name,
        String description,
        @NotBlank String frequency,
        Integer dayOfMonth,
        UUID startPeriodId,
        UUID endPeriodId,
        String journalSourceCode,
        String journalCategoryCode,
        String reference,
        @NotEmpty @Valid List<RecurringLineRequest> lines,
        @NotBlank String createdBy
) {
}
