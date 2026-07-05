package com.evyoog.gl.aie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AieImportRequest(
        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "sourceSystem is required")
        String sourceSystem,

        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        // Recorded on the batch for traceability. The Posting Engine resolves
        // the Legal Entity's actual primary Ledger itself — this pipeline does
        // the same for enrichment, rather than trusting a caller-supplied value.
        UUID ledgerId,

        @NotNull(message = "accountingPeriodId is required")
        UUID accountingPeriodId,

        String batchReference,

        String description,

        @NotBlank(message = "createdBy is required")
        String createdBy,

        @NotEmpty(message = "lines is required")
        @Valid
        List<AieLineRequest> lines
) {
}
