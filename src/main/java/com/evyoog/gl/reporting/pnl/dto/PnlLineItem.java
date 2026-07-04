package com.evyoog.gl.reporting.pnl.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PnlLineItem(
        UUID accountId,
        String accountCode,
        String accountName,
        String accountQualifier,
        Boolean isSummary,
        Boolean isPostable,
        Integer displayOrder,
        BigDecimal periodToDateDr,
        BigDecimal periodToDateCr,
        BigDecimal ytdDr,
        BigDecimal ytdCr,
        BigDecimal netAmount,
        List<PnlLineItem> children
) {
}
