package com.evyoog.gl.tds.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
public record TdsReportResponse(
        UUID legalEntityId,
        String legalEntityName,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        LocalDate generatedAt,

        List<TdsSectionSummary> sectionSummaries,
        BigDecimal grandTotalPayments,
        BigDecimal grandTotalTds,
        Integer totalTransactions
) {
}
