package com.evyoog.gl.posting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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
public class PostingLineRequest {

    private Map<String, String> accountCombination;
    private UUID naturalAccountValueId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String currencyCode;
    private String description;
    private Boolean gstApplicable;
    private String gstType;
    private Boolean tdsApplicable;
    private String tdsSection;
    private Map<String, Object> extendedAttributes;
}
