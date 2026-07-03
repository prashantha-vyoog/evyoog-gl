package com.evyoog.gl.period.dto;

import jakarta.validation.constraints.NotNull;

public record GeneratePeriodsRequest(

        @NotNull(message = "fiscalYear is required")
        Integer fiscalYear
) {
}
