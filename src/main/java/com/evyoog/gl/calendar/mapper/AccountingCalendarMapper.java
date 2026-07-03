package com.evyoog.gl.calendar.mapper;

import com.evyoog.gl.calendar.domain.AccountingCalendar;
import com.evyoog.gl.calendar.dto.AccountingCalendarResponse;
import com.evyoog.gl.calendar.dto.CreateCalendarRequest;
import com.evyoog.gl.calendar.dto.UpdateCalendarRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AccountingCalendarMapper {

    @Mapping(target = "fiscalYearStartMonth", ignore = true)
    @Mapping(target = "fiscalYearStartDay", ignore = true)
    @Mapping(target = "periodType", ignore = true)
    @Mapping(target = "periodsPerYear", ignore = true)
    AccountingCalendar toEntity(CreateCalendarRequest request);

    @Mapping(source = "ledger.id", target = "ledgerId")
    @Mapping(source = "ledger.name", target = "ledgerName")
    @Mapping(source = "active", target = "isActive")
    @Mapping(target = "generatedPeriodCount", ignore = true)
    @Mapping(target = "currentFiscalYear", ignore = true)
    AccountingCalendarResponse toResponse(AccountingCalendar entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateCalendarRequest request, @MappingTarget AccountingCalendar entity);
}
