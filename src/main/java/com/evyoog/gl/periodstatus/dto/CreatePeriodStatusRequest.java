package com.evyoog.gl.periodstatus.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePeriodStatusRequest(
        @NotNull UUID legalEntityId,
        @NotNull UUID accountingPeriodId
) {
}
