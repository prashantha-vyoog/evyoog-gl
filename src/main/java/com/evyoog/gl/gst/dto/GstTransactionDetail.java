package com.evyoog.gl.gst.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record GstTransactionDetail(
        UUID journalLineId,
        UUID journalHeaderId,
        String journalNumber,
        LocalDate glDate,
        String accountCode,
        String accountName,
        String gstType,
        String transactionType,
        BigDecimal amount,
        Boolean tdsApplicable,
        String tdsSection
) {
}
