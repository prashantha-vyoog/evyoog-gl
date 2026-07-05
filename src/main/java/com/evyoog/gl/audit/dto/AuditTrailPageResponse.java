package com.evyoog.gl.audit.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record AuditTrailPageResponse(
        List<AuditTrailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
