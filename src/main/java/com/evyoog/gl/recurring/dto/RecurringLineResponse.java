package com.evyoog.gl.recurring.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Builder
public record RecurringLineResponse(
        Map<String, String> accountCombination,
        UUID naturalAccountValueId,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String description
) {
}
