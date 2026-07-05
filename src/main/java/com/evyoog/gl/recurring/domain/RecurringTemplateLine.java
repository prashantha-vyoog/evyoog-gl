package com.evyoog.gl.recurring.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * One line inside the {@code lines} JSONB column of RecurringJournalTemplate.
 * Stored as a typed POJO (not Map&lt;String,Object&gt;) so Jackson round-trips
 * debitAmount/creditAmount as BigDecimal instead of Double when read back
 * from JSONB via Hibernate 7's native JSON mapping.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTemplateLine {

    private Map<String, String> accountCombination;
    private UUID naturalAccountValueId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
}
