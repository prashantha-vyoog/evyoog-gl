package com.evyoog.gl.wizard.dto;

import java.util.UUID;

public record ProvisionedBusinessUnit(
        UUID businessUnitId,
        String businessUnitCode,
        String businessUnitName,
        String stateName,
        String stateCode
) {
}
