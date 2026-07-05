package com.evyoog.gl.recurring.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record RecurringLineRequest(
        @NotNull Map<String, String> accountCombination,
        @NotNull UUID naturalAccountValueId,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String description
) {
}
