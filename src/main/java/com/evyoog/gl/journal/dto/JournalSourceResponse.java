package com.evyoog.gl.journal.dto;

import java.util.UUID;

public record JournalSourceResponse(
        UUID id,
        String code,
        String name,
        String description,
        Boolean requiresApproval
) {
}
