package com.evyoog.gl.period.mapper;

import com.evyoog.gl.period.domain.AccountingPeriod;
import com.evyoog.gl.period.dto.AccountingPeriodResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountingPeriodMapper {

    @Mapping(source = "accountingCalendar.id", target = "accountingCalendarId")
    @Mapping(source = "accountingCalendar.name", target = "calendarName")
    AccountingPeriodResponse toResponse(AccountingPeriod entity);
}
