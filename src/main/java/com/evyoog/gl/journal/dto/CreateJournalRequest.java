package com.evyoog.gl.journal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateJournalRequest(
        @NotNull(message = "legalEntityId is required")
        UUID legalEntityId,

        @NotNull(message = "journalSourceId is required")
        UUID journalSourceId,

        @NotNull(message = "journalCategoryId is required")
        UUID journalCategoryId,

        String description,

        @NotNull(message = "glDate is required")
        LocalDate glDate,

        // Defaults to glDate when not provided.
        LocalDate accountingDate,

        // Defaults to "INR" when not provided.
        String currencyCode,

        // Defaults to 1.0 when not provided.
        BigDecimal exchangeRate,

        String externalReference,

        String notes,

        Map<String, Object> extendedAttributes,

        @NotNull(message = "lines is required")
        @Size(min = 2, message = "A journal entry must have at least two lines")
        @Valid
        List<CreateJournalLineRequest> lines
) {
}
