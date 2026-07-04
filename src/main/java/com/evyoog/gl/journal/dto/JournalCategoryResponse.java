package com.evyoog.gl.journal.dto;

import java.util.UUID;

public record JournalCategoryResponse(
        UUID id,
        String code,
        String name,
        String description
) {
}
