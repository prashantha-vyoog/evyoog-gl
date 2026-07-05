package com.evyoog.gl.recurring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateRecurringJournalRequest(
        @NotNull UUID targetPeriodId,
        @NotBlank String generatedBy
) {
}
