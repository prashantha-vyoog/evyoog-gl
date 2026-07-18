package com.evyoog.gl.reporting.cashflow.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CashFlowLineItem(
        String description,
        BigDecimal amount,
        String itemType
) {
    public static CashFlowLineItem of(String description, BigDecimal amount, String itemType) {
        return CashFlowLineItem.builder()
                .description(description)
                .amount(amount)
                .itemType(itemType)
                .build();
    }
}
