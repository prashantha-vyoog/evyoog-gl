package com.evyoog.gl.reporting.balancesheet.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record BalanceSheetLineItem(
        UUID accountId,
        String accountCode,
        String accountName,
        String accountQualifier,
        Boolean isSummary,
        Boolean isPostable,
        Integer displayOrder,
        BigDecimal beginningBalance,
        BigDecimal periodToDateDr,
        BigDecimal periodToDateCr,
        BigDecimal endingBalance,
        List<BalanceSheetLineItem> children
) {
}
