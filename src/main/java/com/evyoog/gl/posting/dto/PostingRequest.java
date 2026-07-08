package com.evyoog.gl.posting.dto;

import com.evyoog.gl.posting.domain.JournalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal DTO — the contract between the Posting Engine (GL-15) and its
 * callers (GL-11 Manual Journal Entry, GL-16 AIE Import). Not exposed in any
 * REST API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingRequest {

    private UUID legalEntityId;
    private UUID accountingPeriodId;
    private UUID journalSourceId;
    private UUID journalCategoryId;
    private String description;
    private LocalDate glDate;
    private LocalDate accountingDate;
    private String currencyCode;
    private BigDecimal exchangeRate;
    private List<PostingLineRequest> lines;
    private String performedBy;
    private String externalReference;
    private Map<String, Object> extendedAttributes;
    // EVENT_ONLY mode payload
    private Map<String, Object> eventPayload;
    // DRAFT or straight to POSTED
    private JournalStatus initialStatus;
}
