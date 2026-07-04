package com.evyoog.gl.reporting.trialbalance.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TrialBalanceLine(
        String accountCode,
        String accountName,
        String accountQualifier,
        String normalBalance,
        BigDecimal beginningBalance,
        BigDecimal periodToDateDr,
        BigDecimal periodToDateCr,
        BigDecimal yearToDateDr,
        BigDecimal yearToDateCr,
        BigDecimal endingBalance,
        BigDecimal debitBalance,
        BigDecimal creditBalance
) {
}
