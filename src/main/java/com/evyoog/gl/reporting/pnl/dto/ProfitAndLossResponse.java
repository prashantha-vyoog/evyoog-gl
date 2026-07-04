package com.evyoog.gl.reporting.pnl.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
public record ProfitAndLossResponse(
        UUID legalEntityId,
        String legalEntityName,
        String legalEntityCode,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        String financeMode,
        LocalDate generatedAt,
        List<PnlLineItem> revenueItems,
        BigDecimal totalRevenue,
        List<PnlLineItem> expenseItems,
        BigDecimal totalExpenses,
        BigDecimal grossProfit,
        BigDecimal netIncome,
        Boolean isProfitable
) {
}
