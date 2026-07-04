package com.evyoog.gl.balance.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CarryForwardResponse(
        UUID fromPeriodId,
        String fromPeriodName,
        UUID toPeriodId,
        String toPeriodName,
        Integer balancesCarriedForward,
        List<String> accountsCarriedForward
) {
}
