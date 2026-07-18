package com.evyoog.gl.reporting.cashflow.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record CashFlowSection(
        String sectionCode,
        String sectionName,
        List<CashFlowLineItem> items,
        BigDecimal totalAmount
) {
}
