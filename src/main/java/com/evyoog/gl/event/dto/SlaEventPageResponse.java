package com.evyoog.gl.event.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SlaEventPageResponse(
        List<SlaEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
