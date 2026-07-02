package com.evyoog.gl.ledger.dto;

import com.evyoog.gl.ledger.domain.FinanceMode;
import jakarta.validation.constraints.NotNull;

public record UpdateFinanceModeRequest(

        @NotNull(message = "financeMode is required")
        FinanceMode financeMode
) {
}
