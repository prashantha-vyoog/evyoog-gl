package com.evyoog.gl.wizard.dto;

import com.evyoog.gl.wizard.domain.BusinessType;
import com.evyoog.gl.wizard.domain.IndianState;
import com.evyoog.gl.wizard.domain.LegalStructure;
import com.evyoog.gl.wizard.domain.SessionPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record WizardAnswersRequest(

        @NotNull(message = "contextId is required")
        UUID contextId,

        @NotBlank(message = "companyName is required")
        @Size(max = 200, message = "companyName must be at most 200 characters")
        String companyName,

        @NotNull(message = "legalStructure is required")
        LegalStructure legalStructure,

        @NotNull(message = "businessType is required")
        BusinessType businessType,

        @NotNull(message = "states is required")
        @Size(min = 1, max = 10, message = "states must contain between 1 and 10 entries")
        List<IndianState> states,

        @NotNull(message = "startMonth is required")
        @Min(value = 1, message = "startMonth must be between 1 and 12")
        @Max(value = 12, message = "startMonth must be between 1 and 12")
        Integer startMonth,

        @NotNull(message = "startYear is required")
        Integer startYear,

        Boolean hasOwnCoa,

        String industryTemplate,

        SessionPurpose sessionPurpose
) {
}
