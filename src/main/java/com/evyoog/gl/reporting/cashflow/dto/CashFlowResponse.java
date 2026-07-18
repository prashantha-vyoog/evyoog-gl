package com.evyoog.gl.reporting.cashflow.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CashFlowResponse(
        UUID legalEntityId,
        String legalEntityName,
        String legalEntityCode,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        String financeMode,
        String method,
        LocalDate generatedAt,
        CashFlowSection operatingActivities,
        CashFlowSection investingActivities,
        CashFlowSection financingActivities,
        BigDecimal netCashChange,
        BigDecimal openingCashBalance,
        BigDecimal closingCashBalance,
        Boolean isPositiveCashFlow
) {
}
