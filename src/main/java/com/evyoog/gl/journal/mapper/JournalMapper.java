package com.evyoog.gl.journal.mapper;

import com.evyoog.gl.journal.dto.JournalLineResponse;
import com.evyoog.gl.journal.dto.JournalResponse;
import com.evyoog.gl.journal.dto.JournalSummaryResponse;
import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface JournalMapper {

    @Mapping(source = "legalEntity.id", target = "legalEntityId")
    @Mapping(source = "legalEntity.name", target = "legalEntityName")
    @Mapping(source = "ledger.id", target = "ledgerId")
    @Mapping(source = "ledger.name", target = "ledgerName")
    @Mapping(source = "ledger.financeMode", target = "financeMode")
    @Mapping(source = "accountingPeriod.id", target = "accountingPeriodId")
    @Mapping(source = "accountingPeriod.name", target = "periodName")
    @Mapping(source = "accountingPeriod.fiscalYear", target = "fiscalYear")
    @Mapping(source = "journalSource.id", target = "journalSourceId")
    @Mapping(source = "journalSource.code", target = "journalSourceCode")
    @Mapping(source = "journalSource.name", target = "journalSourceName")
    @Mapping(source = "journalCategory.id", target = "journalCategoryId")
    @Mapping(source = "journalCategory.code", target = "journalCategoryCode")
    @Mapping(source = "journalCategory.name", target = "journalCategoryName")
    JournalResponse toResponse(JournalHeader entity);

    @Mapping(source = "naturalAccount.id", target = "naturalAccountValueId")
    @Mapping(source = "naturalAccount.code", target = "accountCode")
    @Mapping(source = "naturalAccount.name", target = "accountName")
    JournalLineResponse toLineResponse(JournalLine entity);

    @Mapping(source = "legalEntity.name", target = "legalEntityName")
    @Mapping(source = "ledger.name", target = "ledgerName")
    @Mapping(source = "accountingPeriod.name", target = "periodName")
    @Mapping(source = "journalSource.code", target = "journalSourceCode")
    @Mapping(source = "journalCategory.code", target = "journalCategoryCode")
    JournalSummaryResponse toSummary(JournalHeader entity);
}
