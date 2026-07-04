package com.evyoog.gl.reporting.ledger.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record AccountLedgerResponse(
        UUID naturalAccountValueId,
        String accountCode,
        String accountName,
        String accountQualifier,
        String normalBalance,
        UUID legalEntityId,
        String legalEntityName,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        BigDecimal openingBalance,
        List<AccountLedgerEntry> entries,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance,
        Integer entryCount
) {
}
