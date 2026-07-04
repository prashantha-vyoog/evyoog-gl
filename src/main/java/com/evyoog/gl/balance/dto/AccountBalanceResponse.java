package com.evyoog.gl.balance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID id,
        UUID legalEntityId,
        String legalEntityName,
        UUID accountingPeriodId,
        String periodName,
        String fiscalYear,
        UUID naturalAccountValueId,
        String accountCode,
        String accountName,
        String accountQualifier,
        String normalBalance,
        Map<String, String> accountCombination,
        BigDecimal beginningBalance,
        BigDecimal periodToDateDr,
        BigDecimal periodToDateCr,
        BigDecimal yearToDateDr,
        BigDecimal yearToDateCr,
        BigDecimal endingBalance,
        Instant updatedAt
) {
}
