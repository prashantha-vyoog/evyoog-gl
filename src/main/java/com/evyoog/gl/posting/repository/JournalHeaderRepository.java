package com.evyoog.gl.posting.repository;

import com.evyoog.gl.posting.domain.JournalHeader;
import com.evyoog.gl.posting.domain.JournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface JournalHeaderRepository extends JpaRepository<JournalHeader, UUID> {

    boolean existsByJournalNumber(String journalNumber);

    long countByCreatedAtAfter(Instant instant);

    List<JournalHeader> findByLegalEntityId(UUID legalEntityId);

    List<JournalHeader> findByAccountingPeriodId(UUID accountingPeriodId);

    @Query("""
            select jh from JournalHeader jh
            where (:legalEntityId is null or jh.legalEntity.id = :legalEntityId)
            and (:status is null or jh.status = :status)
            and (:accountingPeriodId is null or jh.accountingPeriod.id = :accountingPeriodId)
            and (:sourceCode is null or jh.journalSource.code = :sourceCode)
            and (:fromDate is null or jh.glDate >= :fromDate)
            and (:toDate is null or jh.glDate <= :toDate)
            """)
    Page<JournalHeader> search(@Param("legalEntityId") UUID legalEntityId,
                                @Param("status") JournalStatus status,
                                @Param("accountingPeriodId") UUID accountingPeriodId,
                                @Param("sourceCode") String sourceCode,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                Pageable pageable);
}
