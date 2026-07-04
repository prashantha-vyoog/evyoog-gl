package com.evyoog.gl.balance.mapper;

import com.evyoog.gl.balance.dto.AccountBalanceResponse;
import com.evyoog.gl.posting.domain.AccountBalance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountBalanceMapper {

    @Mapping(source = "legalEntity.id", target = "legalEntityId")
    @Mapping(source = "legalEntity.name", target = "legalEntityName")
    @Mapping(source = "accountingPeriod.id", target = "accountingPeriodId")
    @Mapping(source = "accountingPeriod.name", target = "periodName")
    @Mapping(source = "accountingPeriod.fiscalYear", target = "fiscalYear")
    @Mapping(source = "naturalAccount.id", target = "naturalAccountValueId")
    @Mapping(source = "naturalAccount.code", target = "accountCode")
    @Mapping(source = "naturalAccount.name", target = "accountName")
    @Mapping(source = "naturalAccount.accountQualifier", target = "accountQualifier")
    @Mapping(source = "naturalAccount.normalBalance", target = "normalBalance")
    @Mapping(target = "endingBalance", expression = "java(entity.getBeginningBalance()"
            + ".add(entity.getPeriodToDateDr()).subtract(entity.getPeriodToDateCr()))")
    AccountBalanceResponse toResponse(AccountBalance entity);
}
