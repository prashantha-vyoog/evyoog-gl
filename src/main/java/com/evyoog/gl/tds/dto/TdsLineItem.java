package com.evyoog.gl.tds.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record TdsLineItem(
        String journalNumber,
        LocalDate glDate,
        String tdsSection,
        String sectionDescription,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal tdsAmount,
        String description
) {
}
