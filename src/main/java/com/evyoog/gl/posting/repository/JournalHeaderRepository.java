package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.JournalHeader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JournalHeaderRepository extends JpaRepository<JournalHeader, UUID> {

    boolean existsByJournalNumber(String journalNumber);

    long countByCreatedAtAfter(Instant instant);

    List<JournalHeader> findByLegalEntityId(UUID legalEntityId);

    List<JournalHeader> findByAccountingPeriodId(UUID accountingPeriodId);
}
