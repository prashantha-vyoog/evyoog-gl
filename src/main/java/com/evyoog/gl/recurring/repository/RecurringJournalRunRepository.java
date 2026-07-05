package com.evyoog.gl.recurring.repository;

import com.evyoog.gl.recurring.domain.RecurringJournalRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringJournalRunRepository extends JpaRepository<RecurringJournalRun, UUID> {

    boolean existsByTemplateIdAndAccountingPeriodId(UUID templateId, UUID accountingPeriodId);

    List<RecurringJournalRun> findByTemplateIdOrderByGeneratedAtDesc(UUID templateId);
}
