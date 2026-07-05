package com.evyoog.gl.reversal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReversalRequest(
        @NotNull UUID targetPeriodId,
        @NotBlank String reversedBy,
        String reason
) {
}
