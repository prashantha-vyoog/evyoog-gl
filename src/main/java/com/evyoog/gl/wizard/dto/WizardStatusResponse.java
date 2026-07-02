package com.evyoog.gl.wizard.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WizardStatusResponse(
        UUID contextId,
        boolean isCompleted,
        Instant completedAt,
        Map<String, Object> provisioningAnswers
) {
}
