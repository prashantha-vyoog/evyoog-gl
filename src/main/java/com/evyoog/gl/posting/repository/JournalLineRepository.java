package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.JournalLine;
import com.evyoog.gl.posting.domain.JournalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    List<JournalLine> findByJournalHeaderId(UUID journalHeaderId);

    List<JournalLine> findByNaturalAccountIdAndJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndJournalHeader_Status(
            UUID naturalAccountId, UUID legalEntityId, UUID accountingPeriodId, JournalStatus status);

    List<JournalLine> findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndGstApplicableTrueAndJournalHeader_Status(
            UUID legalEntityId, UUID accountingPeriodId, JournalStatus status);

    List<JournalLine> findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsApplicableTrueAndJournalHeader_Status(
            UUID legalEntityId, UUID accountingPeriodId, JournalStatus status);

    List<JournalLine> findByJournalHeader_LegalEntityIdAndJournalHeader_AccountingPeriodIdAndTdsSectionAndJournalHeader_Status(
            UUID legalEntityId, UUID accountingPeriodId, String tdsSection, JournalStatus status);
}
