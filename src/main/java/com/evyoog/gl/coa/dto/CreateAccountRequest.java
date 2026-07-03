package com.evyoog.gl.coa.dto;

import com.evyoog.gl.dimension.domain.AccountQualifier;
import com.evyoog.gl.dimension.domain.NormalBalance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateAccountRequest(

        @NotNull(message = "ledgerId is required")
        UUID ledgerId,

        @NotBlank(message = "code is required")
        @Size(max = 30, message = "code must be at most 30 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        UUID parentAccountId,

        @NotNull(message = "qualifier is required")
        AccountQualifier qualifier,

        Boolean isSummary,

        Boolean isPostable,

        NormalBalance normalBalance,

        Boolean gstApplicable,

        Boolean tdsApplicable,

        @Size(max = 10, message = "tdsSection must be at most 10 characters")
        String tdsSection,

        Integer displayOrder,

        // Intercompany — required when the Ledger's INTERCOMPANY dimension value is created via this endpoint
        UUID counterpartyLegalEntityId,

        // Cost Centre metadata
        String ccManagerName,

        String ccManagerEmail,

        String ccDepartment,

        // Date range
        LocalDate validFrom,

        LocalDate validTo,

        // Budget control
        Boolean budgetControlled
) {
}
