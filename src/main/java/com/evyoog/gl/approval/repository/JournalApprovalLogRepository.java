package com.evyoog.gl.approval.repository;

import com.evyoog.gl.approval.domain.JournalApprovalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalApprovalLogRepository extends JpaRepository<JournalApprovalLog, UUID> {

    List<JournalApprovalLog> findByJournalHeaderIdOrderByCreatedAtAsc(UUID journalHeaderId);
}
