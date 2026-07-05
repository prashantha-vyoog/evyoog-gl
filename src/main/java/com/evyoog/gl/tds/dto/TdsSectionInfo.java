package com.evyoog.gl.tds.dto;

import lombok.Builder;

@Builder
public record TdsSectionInfo(
        String tdsSection,
        String description
) {
}
