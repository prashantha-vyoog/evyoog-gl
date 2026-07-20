package com.evyoog.gl.coa.excel.dto;

/**
 * One data row parsed from a GL-06 Chart of Accounts Excel upload, before
 * any validation. All String fields are raw cell values (trimmed) — the
 * caller decides how to validate/apply defaults.
 */
public record ParsedAccountRow(
        int rowNumber,
        String code,
        String name,
        String qualifier,
        String parentCode,
        Boolean isPostable,
        Boolean isSummary,
        String normalBalance,
        String description
) {
}
