package com.evyoog.gl.balance.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CarryForwardRequest(
        @NotNull UUID legalEntityId,
        @NotNull UUID fromPeriodId,
        @NotNull UUID toPeriodId
) {
}
