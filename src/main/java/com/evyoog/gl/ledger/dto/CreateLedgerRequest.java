package com.evyoog.gl.ledger.dto;

import com.evyoog.gl.enterprise.domain.AccountingStandard;
import com.evyoog.gl.ledger.domain.FinanceMode;
import com.evyoog.gl.ledger.domain.LedgerCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLedgerRequest(

        @NotBlank(message = "code is required")
        @Size(max = 30, message = "code must be at most 30 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "financeMode is required")
        FinanceMode financeMode,

        LedgerCategory ledgerCategory,

        @Size(min = 3, max = 3, message = "functionalCurrency must be a 3-letter ISO currency code")
        String functionalCurrency,

        AccountingStandard accountingStandard
) {
}
