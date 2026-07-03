package com.evyoog.gl.periodstatus.mapper;

import com.evyoog.gl.periodstatus.domain.PeriodStatus;
import com.evyoog.gl.periodstatus.dto.PeriodStatusResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PeriodStatusMapper {

    @Mapping(source = "legalEntity.id", target = "legalEntityId")
    @Mapping(source = "legalEntity.name", target = "legalEntityName")
    @Mapping(source = "accountingPeriod.id", target = "accountingPeriodId")
    @Mapping(source = "accountingPeriod.name", target = "periodName")
    @Mapping(source = "accountingPeriod.fiscalYear", target = "fiscalYear")
    PeriodStatusResponse toResponse(PeriodStatus entity);
}
