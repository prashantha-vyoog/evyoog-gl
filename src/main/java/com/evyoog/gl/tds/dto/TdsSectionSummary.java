package com.evyoog.gl.tds.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TdsSectionSummary(
        String tdsSection,
        String sectionDescription,
        Integer transactionCount,
        BigDecimal totalPaymentAmount,
        BigDecimal totalTdsAmount,
        BigDecimal effectiveRate
) {
}
