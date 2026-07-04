package com.evyoog.gl.balance.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AccountBalanceSummaryResponse(
        UUID legalEntityId,
        String legalEntityName,
        UUID accountingPeriodId,
        String periodName,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome
) {
}
